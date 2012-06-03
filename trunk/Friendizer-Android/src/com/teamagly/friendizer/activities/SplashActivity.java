package com.teamagly.friendizer.activities;

import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.facebook.android.AsyncFacebookRunner;
import com.facebook.android.DialogError;
import com.facebook.android.Facebook.DialogListener;
import com.facebook.android.FacebookError;
import com.teamagly.friendizer.R;
import com.teamagly.friendizer.model.FacebookUser;
import com.teamagly.friendizer.model.User;
import com.teamagly.friendizer.utils.BaseRequestListener;
import com.teamagly.friendizer.utils.DeviceRegistrar;
import com.teamagly.friendizer.utils.ServerFacade;
import com.teamagly.friendizer.utils.SessionEvents;
import com.teamagly.friendizer.utils.SessionEvents.AuthListener;
import com.teamagly.friendizer.utils.SessionEvents.LogoutListener;
import com.teamagly.friendizer.utils.SessionStore;
import com.teamagly.friendizer.utils.Util;
import com.teamagly.friendizer.utils.Utility;

/**
 * The login flow is as follows: Facebook login -> C2DM registration -> friendizer login
 */
public class SplashActivity extends SherlockActivity {
    private final String TAG = getClass().getName();
    private Handler handler;
    private ImageView loginButton;
    UserRequestListener userRequestListener;
    private String requestID;
    private Context context = this;
    private ProgressDialog dialogC2DM;
    private ProgressDialog dialogFriendizer;

    /**
     * A {@link BroadcastReceiver} to receive the response from a register or unregister request, and to update the UI.
     */
    private final BroadcastReceiver mUpdateUIReceiver = new BroadcastReceiver() {
	@Override
	public void onReceive(Context context, Intent intent) {
	    String accountName = intent.getStringExtra(Util.ACCOUNT_NAME);
	    int status = intent.getIntExtra(Util.CONNECTION_STATUS, DeviceRegistrar.ERROR_STATUS);
	    String message = null;
	    String connectionStatus = Util.DISCONNECTED;
	    if (status == DeviceRegistrar.REGISTERED_STATUS) {
		message = getResources().getString(R.string.registration_succeeded);
		connectionStatus = Util.CONNECTED;
		enterFriendizer();
	    } else if (status == DeviceRegistrar.UNREGISTERED_STATUS) {
		message = getResources().getString(R.string.unregistration_succeeded);
	    } else {
		message = getResources().getString(R.string.registration_error);
	    }
	    handler.post(new Runnable() {
		@Override
		public void run() {
		    dialogC2DM.dismiss();
		}
	    });

	    // Set connection status
	    SharedPreferences prefs = Util.getSharedPreferences(context);
	    prefs.edit().putString(Util.CONNECTION_STATUS, connectionStatus).commit();

	    // Display a notification
	    Util.generateNotification(context, String.format(message, accountName));
	}
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	getSupportActionBar().hide(); // Hide the action bar
	setContentView(R.layout.splash);
	handler = new Handler();

	if (!isOnline()) {
	    ((TextView) findViewById(R.id.status)).setText("No Internet connection, please try again later");
	    return;
	}

	// Register a receiver to provide register/unregister notifications
	registerReceiver(mUpdateUIReceiver, new IntentFilter(Util.UPDATE_UI_INTENT));

	Intent intent = getIntent();
	// Parse any incoming Facebook notifications and save
	Uri intentUri = getIntent().getData();
	if (intentUri != null) {
	    String requestIdParam = intentUri.getQueryParameter("request_ids");
	    if (requestIdParam != null) {
		String array[] = requestIdParam.split(",");
		requestID = array[0];
	    }
	}

	// Listener for the login button
	loginButton = (ImageView) findViewById(R.id.loginButton);
	loginButton.setOnClickListener(new View.OnClickListener() {
	    public void onClick(View v) {
		loginToFacebook();
	    }
	});

	if (intent.getBooleanExtra("logout", false)) {
	    loginButton.setVisibility(View.VISIBLE);
	    logout();
	    return;
	}

	SessionEvents.addAuthListener(new FBLoginListener());
	SessionEvents.addLogoutListener(new FBLogoutListener());

	dialogC2DM = new ProgressDialog(context);
	dialogC2DM.setMessage("Connecting to Google, please wait...");
	dialogC2DM.setCancelable(false);

	dialogFriendizer = new ProgressDialog(context);
	dialogFriendizer.setMessage("Logging in, please wait...");
	dialogFriendizer.setCancelable(false);

	// Restore the Facebook session if one exists
	if (SessionStore.restore(Utility.getInstance().facebook, this)) {
	    // Already logged in to Facebook -> extend access token and request Facebook data to proceed
	    Utility.getInstance().facebook.extendAccessTokenIfNeeded(context, null);
	    requestFacebookUserData();
	} else
	    // Not logged in to Facebook -> wait for user action
	    loginButton.setVisibility(View.VISIBLE);
    }

    protected void loginToFacebook() {
	if (!Utility.getInstance().facebook.isSessionValid()) {
	    // Authorize
	    Utility.getInstance().facebook.authorize(SplashActivity.this, new String[] { "user_activities", "user_checkins",
		    "user_interests", "user_likes", "user_birthday", "user_relationships" }, 0, new LoginDialogListener());
	}
    }

    @Override
    public void onDestroy() {
	unregisterReceiver(mUpdateUIReceiver);
	super.onDestroy();
    }

    /**
     * Logs out the user from Facebook
     */
    private void logout() {
	try {
	    SessionEvents.onLogoutBegin();
	    AsyncFacebookRunner asyncRunner = new AsyncFacebookRunner(Utility.getInstance().facebook);
	    asyncRunner.logout(getBaseContext(), new LogoutRequestListener());
	} catch (Exception e) {
	    Log.e(TAG, e.getMessage());
	}
    }

    /**
     * @return true iff the user is connected to the Internet
     */
    public boolean isOnline() {
	ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	NetworkInfo netInfo = cm.getActiveNetworkInfo();
	if (netInfo != null && netInfo.isConnected()) {
	    return true;
	}
	return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
	super.onActivityResult(requestCode, resultCode, data);
	Utility.getInstance().facebook.authorizeCallback(requestCode, resultCode, data);
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onBackPressed()
     */
    @Override
    public void onBackPressed() {
	// Quit the app
	moveTaskToBack(true);
    }

    /*
     * Request user details from Facebook
     */
    public void requestFacebookUserData() {
	Bundle params = new Bundle();
	params.putString("fields", "name, first_name, picture, birthday, gender");
	// Send a new request only if there are none currently
	if ((userRequestListener == null) || (!userRequestListener.completed))
	    userRequestListener = new UserRequestListener();
	Utility.getInstance().mAsyncRunner.request("me", params, userRequestListener);
    }

    /*
     * Callback for fetching current user's name, picture, uid.
     */
    public class UserRequestListener extends BaseRequestListener {
	public boolean completed = false;

	@Override
	public void onComplete(final String response, final Object state) {
	    JSONObject jsonObject;
	    try {
		jsonObject = new JSONObject(response);
		final User userInfo = new User(new FacebookUser(jsonObject));
		Utility.getInstance().userInfo = userInfo;

		/*
		 * Login/Register to Google Accounts
		 */
		SharedPreferences prefs = Util.getSharedPreferences(context);
		String connectionStatus = prefs.getString(Util.CONNECTION_STATUS, Util.DISCONNECTED);
		// Disconnected -> register to C2DM
		if (Util.DISCONNECTED.equals(connectionStatus)) {
		    // Move to the Accounts activity and show a progress dialog
		    startActivity(new Intent(context, AccountsActivity.class));
		    handler.post(new Runnable() {
			@Override
			public void run() {
			    dialogC2DM.show();
			}
		    });
		}
		// Connecting -> show progress dialog
		else if (Util.CONNECTING.equals(connectionStatus))
		    handler.post(new Runnable() {
			@Override
			public void run() {
			    dialogC2DM.show();
			}
		    });
		// Connected -> login to friendizer to proceed
		else if (Util.CONNECTED.equals(connectionStatus)) {
		    handler.post(new Runnable() {
			@Override
			public void run() {
			    dialogFriendizer.show(); // Show a progress dialog
			}
		    });
		    enterFriendizer();
		}
	    } catch (Exception e) {
		Log.w(TAG, "The response from Facebook: " + response);
		Log.e(TAG, e.getMessage());
	    } finally {
		completed = true;
	    }
	}
    }

    protected void enterFriendizer() {
	handler.post(new Runnable() {
	    @Override
	    public void run() {
		dialogFriendizer.show(); // Show a progress dialog
		new EnterFriendizerTask().execute();
	    }
	});
    }

    /*
     * Login and retrieve the user details from Friendizer Note: Don't use this task directly! use the method enterFriendizer()!
     */
    protected class EnterFriendizerTask extends AsyncTask<Void, Void, Boolean> {

	protected Boolean doInBackground(Void... v) {
	    // Get the deviceRegistrationID
	    SharedPreferences prefs = Util.getSharedPreferences(context);
	    String deviceRegistrationID = prefs.getString(Util.DEVICE_REGISTRATION_ID, "");
	    try {
		Utility.getInstance().userInfo.updateFriendizerData(ServerFacade.login(Utility.getInstance().userInfo.getId(),
			Utility.getInstance().facebook.getAccessToken(), deviceRegistrationID, context));
	    } catch (Exception e) {
		Log.e(TAG, e.getMessage());
		runOnUiThread(new Runnable() {
		    @Override
		    public void run() {
			Toast.makeText(context, "Couldn't login to friendizer!", Toast.LENGTH_LONG).show();
			dialogFriendizer.dismiss(); // Dismiss the progress dialog
		    }
		});
		return false;
	    }
	    return true;
	}

	protected void onPostExecute(Boolean result) {
	    if (result) {
		Toast.makeText(SplashActivity.this, "Welcome " + Utility.getInstance().userInfo.getFirstName() + "!",
			Toast.LENGTH_LONG).show();
		dialogFriendizer.dismiss(); // Dismiss the progress dialog
		// Continue to the main activity
		Intent intent = new Intent(SplashActivity.this, FriendizerActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP); // Clear the activity stack
		startActivity(intent);
		finish();
	    }
	}
    }

    /*
     * The Callback for notifying the application when authorization succeeds or fails.
     */

    public class FBLoginListener implements AuthListener {

	@Override
	public void onAuthSucceed() {
	    SessionStore.save(Utility.getInstance().facebook, SplashActivity.this);
	    requestFacebookUserData();
	}

	@Override
	public void onAuthFail(String error) {
	}
    }

    /*
     * The Callback for notifying the application when log out starts and finishes.
     */
    public class FBLogoutListener implements LogoutListener {
	@Override
	public void onLogoutBegin() {
	    SessionStore.clear(SplashActivity.this);
	}

	@Override
	public void onLogoutFinish() {
	}
    }

    private final class LoginDialogListener implements DialogListener {
	@Override
	public void onComplete(Bundle values) {
	    // Process any available request
	    if (requestID != null) {
		// Just delete the request
		// Toast.makeText(getApplicationContext(), "Incoming request", Toast.LENGTH_SHORT).show();
		Bundle params = new Bundle();
		params.putString("method", "delete");
		Utility.getInstance().mAsyncRunner.request(requestID, params, new RequestIDDeleteRequestListener());
	    }

	    SessionEvents.onLoginSuccess();
	}

	@Override
	public void onFacebookError(FacebookError error) {
	    SessionEvents.onLoginError(error.getMessage());
	}

	@Override
	public void onError(DialogError error) {
	    SessionEvents.onLoginError(error.getMessage());
	}

	@Override
	public void onCancel() {
	    SessionEvents.onLoginError("Action Canceled");
	}
    }

    private class LogoutRequestListener extends BaseRequestListener {
	@Override
	public void onComplete(String response, final Object state) {
	    /*
	     * callback should be run in the original thread, not the background thread
	     */
	    handler.post(new Runnable() {
		@Override
		public void run() {
		    SessionEvents.onLogoutFinish();
		}
	    });
	}
    }

    public class RequestIDDeleteRequestListener extends BaseRequestListener {
	@Override
	public void onComplete(final String response, Object state) {
	};
    }
}