package com.teamagly.friendizer.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.appengine.labs.repackaged.org.json.JSONException;
import com.google.appengine.labs.repackaged.org.json.JSONObject;

public class UserMatching {

    private User user;
    private int matching;

    public UserMatching(User user, int matching) {
	this.user = user;
	this.matching = matching;
    }

    public UserMatching(JSONObject obj) throws JSONException {
	long id = obj.getLong("id");
	long owner = obj.getLong("owner");
	long points = obj.getLong("points");
	int level = obj.getInt("level");
	long money = obj.getLong("money");
	double latitude = obj.getDouble("latitude");
	double longitude = obj.getDouble("longitude");
	String token = obj.getString("token");
	String status = obj.optString("status");
	SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	try {
	    Date since = format.parse(obj.getString("since"));
	    user = new User(id, owner, points, level, money, latitude, longitude, since, token, status);
	    matching = obj.getInt("matching");
	} catch (ParseException e) {
	    throw new JSONException("JSONObject[\"since\"] is not a date.");
	}
    }

    public User getUser() {
	return user;
    }

    public void setUser(User user) {
	this.user = user;
    }

    public int getMatching() {
	return matching;
    }

    public void setMatching(int matching) {
	this.matching = matching;
    }

    public JSONObject toJSONObject() {
	JSONObject obj = new JSONObject();
	try {
	    obj.put("id", user.getId());
	    obj.put("owner", user.getOwner());
	    obj.put("points", user.getPoints());
	    obj.put("level", user.getLevel());
	    obj.put("money", user.getMoney());
	    obj.put("latitude", user.getLatitude());
	    obj.put("longitude", user.getLongitude());
	    obj.put("token", user.getToken());
	    obj.put("status", user.getStatus());
	    SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	    obj.put("since", format.format(user.getSince()));
	    obj.put("matching", matching);
	} catch (JSONException e) {
	}
	return obj;
    }

    @Override
    public String toString() {
	return toJSONObject().toString();
    }
}