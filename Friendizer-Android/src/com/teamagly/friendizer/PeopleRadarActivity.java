package com.teamagly.friendizer;

import java.util.ArrayList;
import java.util.Collections;

import org.json.JSONObject;

import com.teamagly.friendizer.R;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.Toast;

public class PeopleRadarActivity extends ListActivity implements OnItemClickListener {
    ProgressDialog dialog;
    private boolean list_type;
    private GridView gridView;
    private ArrayList<UserInfo> usersList = new ArrayList<UserInfo>();
    private ArrayAdapter<UserInfo> peopleAdapter;
    private int sortBy = 4; // Default order is by matching

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.connections_layout);
	gridView = (GridView) findViewById(R.id.gridview);

	boolean type = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("friends_list_type", false);
	list_type = type;
	updateListType(type);
	requestPeople();
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
	super.onResume();
	boolean type = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("friends_list_type", false);
	if (type != list_type) { // A change occurred -> redraw the view
	    list_type = type;
	    updateListType(type);
	}
    }

    /**
     * Clears the current users list and request the information from Facebook
     */
    protected void requestPeople() {
	dialog = ProgressDialog.show(this, "", getString(R.string.please_wait), true, true);
	usersList.clear();
	try {
	    long[] nearbyUsers = ServerFacade.nearbyUsers(Utility.getInstance().userInfo.id, Utility.DEFAULT_DISTANCE);
	    for (long fbid : nearbyUsers) { // Request the details of each nearby user
		Bundle params = new Bundle();
		params.putString("fields", "name, picture, birthday, gender");
		Utility.getInstance().mAsyncRunner.request(String.valueOf(fbid), params, new UserRequestListener(this));
	    }
	} catch (Exception e) {
	    showToast("An error occured");
	    e.printStackTrace();
	}
	dialog.dismiss();
    }

    protected void updateListType(boolean type) {
	if (type) { // => show in a list
	    gridView.setAdapter(null);
	    peopleAdapter = new FriendsListAdapter(this, R.layout.connection_list_item, usersList);
	    getListView().setOnItemClickListener(this);
	    getListView().setAdapter(peopleAdapter);
	} else { // => show in a GridView
	    getListView().setAdapter(null);
	    peopleAdapter = new FriendsImageAdapter(this, 0, usersList);
	    gridView.setAdapter(peopleAdapter);
	    gridView.setOnItemClickListener(this);
	}
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {
	super.onPause();
	dialog.dismiss(); // Dismiss the dialog
    }

    /*
     * (non-Javadoc)
     * @see android.app.ListActivity#onDestroy()
     */
    @Override
    protected void onDestroy() {
	getListView().setAdapter(null);
	gridView.setAdapter(null);
	super.onDestroy();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
	super.onCreateOptionsMenu(menu);
	menu.clear(); // Clear the main activity's menu
	MenuInflater inflater = getMenuInflater();
	inflater.inflate(R.menu.connections_menu, menu);
	return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
	switch (item.getItemId()) {
	case R.id.settings: // Move to the settings activity
	    startActivity(new Intent(this, FriendsPrefs.class));
	    return true;
	case R.id.invite: // Show the Facebook invitation dialog
	    Bundle params = new Bundle();
	    params.putString("message", getString(R.string.invitation_msg));
	    Utility.getInstance().facebook.dialog(this, "apprequests", params, new BaseDialogListener());
	    return true;
	case R.id.facebook_friends: // Move to my Facebook friends activity
	    Intent intent = new Intent().setClass(PeopleRadarActivity.this, FBFriendsActivity.class);
	    startActivity(intent);
	    return true;
	case R.id.sort:
	    final String[] options = { "Alphabeth", "Value", "Matching", "Distance" };
	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setTitle("Sort By");
	    builder.setItems(options, new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int item) {
		    if (item != sortBy) {
			switch (item) {
			case 0:
			    Collections.sort(usersList, (new Comparators()).new AlphabetComparator());
			    break;
			case 1:
			    Collections.sort(usersList, (new Comparators()).new ValueComparator());
			    break;
			case 2:
			    Collections.sort(usersList, (new Comparators()).new MatchingComparator());
			    break;
			case 3:
			    Collections.sort(usersList, (new Comparators()).new DistanceComparator());
			    break;
			}
			sortBy = item;
			runOnUiThread(new Runnable() {
			    public void run() {
				peopleAdapter.notifyDataSetChanged(); // Notify the adapter (must do from the main thread)
			    }
			});
		    }
		}
	    });
	    AlertDialog alert = builder.create();
	    alert.show();
	    return true;
	case R.id.refresh:
	    requestPeople();
	    return true;
	default:
	    return super.onOptionsItemSelected(item);
	}
    }

    /*
     * (non-Javadoc)
     * @see android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget.AdapterView, android.view.View, int, long)
     */
    @Override
    public void onItemClick(AdapterView<?> arg0, View v, int position, long arg3) {
	// Create an intent with the dude's data
	Intent intent = new Intent().setClass(PeopleRadarActivity.this, FriendProfileActivity.class);
	UserInfo userInfo = usersList.get(position);
	intent.putExtra("user", userInfo);
	startActivity(intent);
    }

    /**
     * Shows a toast
     * 
     * @param msg
     *            a message to show
     */
    public void showToast(final String msg) {
	new Handler().post(new Runnable() {
	    @Override
	    public void run() {
		Toast toast = Toast.makeText(PeopleRadarActivity.this, msg, Toast.LENGTH_LONG);
		toast.show();
	    }
	});
    }

    /*
     * Callback for fetching a user's name, picture, uid.
     */
    public class UserRequestListener extends BaseRequestListener {
	PeopleRadarActivity curActivity;

	public UserRequestListener(PeopleRadarActivity curActivity) {
	    this.curActivity = curActivity;
	}

	@Override
	public void onComplete(final String response, final Object state) {
	    try {
		JSONObject jsonObject = new JSONObject(response);
		UserInfo userInfo = new UserInfo(jsonObject);
		usersList.add(userInfo);
		// Sort the list by matching
		Collections.sort(usersList, (new Comparators()).new MatchingComparator());
		runOnUiThread(new Runnable() {
		    public void run() {
			peopleAdapter.notifyDataSetChanged(); // Notify the adapter (must do from the main thread)
		    }
		});
	    } catch (Exception e) {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
		    @Override
		    public void run() {
			Toast.makeText(getApplicationContext(), "Failed to request user data", Toast.LENGTH_SHORT).show();
		    }
		});
	    }
	}
    }
}