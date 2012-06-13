/**
 * 
 */
package com.teamagly.friendizer.adapters;

import java.util.List;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.teamagly.friendizer.R;
import com.teamagly.friendizer.model.User;
import com.teamagly.friendizer.utils.ImageLoader.Type;
import com.teamagly.friendizer.utils.Utility;

public class FBFriendsAdapter extends FriendsAdapter {
    private final String TAG = getClass().getName();

    /**
     * @param context
     * @param textViewResourceId
     * @param objects
     */
    public FBFriendsAdapter(Context context, int textViewResourceId, List<User> objects) {
	super(context, textViewResourceId, objects);
    }

    /*
     * (non-Javadoc)
     * @see com.teamagly.friendizer.FriendsAdapter#getView(int, android.view.View, android.view.ViewGroup)
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
	View hView = convertView;
	if (convertView == null) {
	    hView = inflater.inflate(R.layout.friend_list_item, null);
	    ViewHolder holder = new ViewHolder();
	    holder.profile_pic = (ImageView) hView.findViewById(R.id.profile_pic);
	    holder.name = (TextView) hView.findViewById(R.id.name);
	    hView.setTag(holder);
	}

	User userInfo = getItem(position);
	ViewHolder holder = (ViewHolder) hView.getTag();
	holder.profile_pic.setImageBitmap(Utility.getInstance().imageLoader.getImage(userInfo.getPicURL(), Type.ROUND_CORNERS));
	holder.name.setText(userInfo.getName());
	return hView;
    }

    class ViewHolder {
	ImageView profile_pic;
	TextView name;
    }

}