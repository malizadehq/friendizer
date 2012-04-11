/**
 * 
 */
package com.teamagly.friendizer;

import java.util.ArrayList;
import java.util.Collections;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * @author Guy
 * 
 *         Note: the child class has to call showLoadingIcon(false) after it's done reloading the data in order to stop the
 *         loading indicator
 */
public abstract class AbstractFriendsListActivity extends ListActivity implements OnItemClickListener {
    private final String TAG = "AbstractFriendsListActivity";
    protected boolean list_type;
    protected GridView gridView;
    protected FriendsAdapter friendsAdapter;
    protected ArrayList<UserInfo> usersList = new ArrayList<UserInfo>();
    protected int sortBy = 0;
    final Handler handler = new Handler();

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	boolean type = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("friends_list_type", false);
	list_type = type;
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
	super.onResume();
	showLoadingIcon(true);
	requestFriends();
	boolean type = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("friends_list_type", false);
	if (type != list_type) { // A change occurred -> redraw the view
	    list_type = type;
	    updateListType(type);
	}
    }

    /**
     * Clears the current users list and request the information from Facebook
     */
    protected abstract void requestFriends();

    protected void updateListType(boolean type) {
	if (list_type) { // => show in a list
	    gridView.setAdapter(null);
	    friendsAdapter = new FriendsListAdapter(this, R.layout.connection_list_item, usersList);
	    getListView().setOnItemClickListener(this);
	    getListView().setAdapter(friendsAdapter);
	} else { // == show in a GridView
	    getListView().setAdapter(null);
	    friendsAdapter = new FriendsImageAdapter(this, 0, usersList);
	    gridView.setAdapter(friendsAdapter);
	    gridView.setOnItemClickListener(this);
	}
    }

    /**
     * @param show
     *            whether to show or hide the loading icon (in the parent activity)
     */
    protected void showLoadingIcon(boolean show) {
	Log.d(TAG, "showLoadingIcon: " + show);
	try {
	    Activity parent = getParent();
	    if (parent != null)
		((FriendizerActivity) parent).actionBar.showProgressBar(show);
	} catch (Exception e) {
	}
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
	    Intent intent = new Intent().setClass(this, FBFriendsActivity.class);
	    startActivity(intent);
	    return true;
	case R.id.sort:
	    final String[] options = { "Alphabeth", "Value", "Matching" };
	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setTitle("Sort by");
	    builder.setSingleChoiceItems(options, 0, new DialogInterface.OnClickListener() {
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
			}
			sortBy = item;
			handler.post(new Runnable() {
			    @Override
			    public void run() {
				friendsAdapter.notifyDataSetChanged(); // Notify the adapter
			    }
			});
		    }
		}
	    });
	    AlertDialog optionsDialog = builder.create();
	    optionsDialog.show();
	    return true;
	case R.id.view_by:
	    // Show a dialog
	    final String[] items = { "Grid", "List" };
	    builder = new AlertDialog.Builder(this);
	    builder.setTitle("View by");
	    int i_list_type = 0;
	    if (list_type)
		i_list_type = 1;
	    builder.setSingleChoiceItems(items, i_list_type, new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int item) {
		    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit();
		    boolean choise = false; // Default is grid
		    if (item == 1) // The user chose list
			choise = true;
		    if (list_type != choise) { // If the user changed the type, redraw the view
			list_type = choise;
			updateListType(choise);
			// Update the preference
			editor.putBoolean("friends_list_type", choise);
			editor.commit();
		    }
		    dialog.dismiss();
		}
	    });
	    builder.show();
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
	UserInfo userInfo = usersList.get(position);
	// Create an intent with the friend's data
	Intent intent = new Intent().setClass(this, FriendProfileActivity.class);
	intent.putExtra("user", userInfo);
	startActivity(intent);
    }

    protected void updateUsersFromFriendizer() {
	// Load each user's details from our servers in the background
	new Thread(new Runnable() {
	    public void run() {
		for (int i = 0; i < usersList.size(); i++) {
		    try {
			usersList.get(i).updateFriendizerData(ServerFacade.userDetails(usersList.get(i).id));
		    } catch (Exception e) {
			Log.e(TAG, "", e);
		    }
		}
		handler.post(new Runnable() {
		    @Override
		    public void run() {
			friendsAdapter.notifyDataSetChanged(); // Notify the adapter
		    }
		});
	    }
	}).start();
    }
}