package com.teamagly.friendizer.activities;

import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import com.google.android.gcm.GCMRegistrar;
import com.teamagly.friendizer.R;
import com.teamagly.friendizer.model.FacebookUser;
import com.teamagly.friendizer.model.FriendizerUser;
import com.teamagly.friendizer.model.User;
import com.teamagly.friendizer.utils.BaseRequestListener;
import com.teamagly.friendizer.utils.ServerFacade;
import com.teamagly.friendizer.utils.SessionEvents;
import com.teamagly.friendizer.utils.SessionEvents.AuthListener;
import com.teamagly.friendizer.utils.SessionEvents.LogoutListener;
import com.teamagly.friendizer.utils.SessionStore;
import com.teamagly.friendizer.utils.Util;
import com.teamagly.friendizer.utils.Utility;

/**
 * The login flow is as follows: Facebook login -> GCM registration -> friendizer login
 */
public class SplashActivity extends SherlockActivity {
	private final String TAG = getClass().getName();
	private Handler handler;
	private ImageView loginButton;
	UserRequestListener userRequestListener;
	private String requestID;
	private Context context = this;
	private ProgressDialog dialogFriendizer;

	// After GCM registration, login to friendizer
	AsyncTask<Void, Void, Boolean> friendizerLoginTask = new AsyncTask<Void, Void, Boolean>() {

		protected Boolean doInBackground(Void... v) {
			// Get the registrationID
			String regID = GCMRegistrar.getRegistrationId(context);
			if (regID == null || regID.length() == 0)
				return false;
			FriendizerUser fzUser = ServerFacade.login(Utility.getInstance().userInfo.getId(),
					Utility.getInstance().facebook.getAccessToken(), regID);
			if (fzUser == null)
				return false;
			else {
				Utility.getInstance().userInfo.updateFriendizerData(fzUser);
				return true;
			}
		}

		protected void onPostExecute(Boolean result) {
			dialogFriendizer.dismiss(); // Dismiss the progress dialog
			if (result) {
				Toast.makeText(context, "Welcome " + Utility.getInstance().userInfo.getFirstName() + "!", Toast.LENGTH_LONG)
						.show();
				// Continue to the main activity
				Intent intent = new Intent(SplashActivity.this, FriendizerActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP); // Clear the
																									// activity
																									// stack
				startActivity(intent);
				finish();
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(context);
				builder.setMessage("An error occured. Please restart the app").setCancelable(false)
						.setPositiveButton("OK", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								finish();
							}
						}).show();
			}
		}
	};

	/**
	 * A {@link BroadcastReceiver} to receive the response from a register or unregister request, and to update the UI.
	 */
	private final BroadcastReceiver mHandleMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			friendizerLoginTask.execute();
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSupportActionBar().hide(); // Hide the action bar
		setContentView(R.layout.splash);
		handler = new Handler();

		if (!isOnline()) {
			TextView txtStatus = (TextView) findViewById(R.id.status);
			txtStatus.setText("No Internet connection!");
			txtStatus.setVisibility(View.VISIBLE);
			return;
		}

		// Register a receiver to provide register/unregister notifications
		registerReceiver(mHandleMessageReceiver, new IntentFilter(Util.UPDATE_UI_INTENT));

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

		dialogFriendizer = new ProgressDialog(context);
		dialogFriendizer.setMessage("Logging in, please wait...");

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
		if (friendizerLoginTask != null)
			friendizerLoginTask.cancel(true);
		unregisterReceiver(mHandleMessageReceiver);
		GCMRegistrar.onDestroy(this);
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
	// @Override
	// public void onBackPressed() {
	// // Quit the app
	// finish();
	// }

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

				handler.post(new Runnable() {
					@Override
					public void run() {
						dialogFriendizer.show();
					}
				});

				/*
				 * Login/Register to GCM
				 */
				GCMRegistrar.checkDevice(context);
				// Make sure the manifest was properly set - comment out this line
				// while developing the app, then uncomment it when it's ready.
				GCMRegistrar.checkManifest(context);
				final String regID = GCMRegistrar.getRegistrationId(context);
				if (regID.equals("")) // Register to GCM
					GCMRegistrar.register(context, Utility.SENDER_ID);
				else
					friendizerLoginTask.execute();
			} catch (Exception e) {
				Log.w(TAG, "The response from Facebook: " + response);
				Log.e(TAG, e.getMessage());
			} finally {
				completed = true;
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
