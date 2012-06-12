package com.teamagly.friendizer.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Gift {
	private Long id;
    private String name;
    private String iconRes;
	private int value;

	public Gift(Long id, String name, String iconRes, int value) {
		this.id = id;
		this.name = name;
		this.iconRes = iconRes;
		this.value = value;
	}
	
	public Gift(JSONObject obj) throws JSONException {
		id = obj.getLong("id");
		name = obj.getString("name");
		iconRes = obj.getString("iconRes");
		value = obj.getInt("value");
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getIconRes() {
		return iconRes;
	}

	public void setIconRes(String iconRes) {
		this.iconRes = iconRes;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}
	
	public JSONObject toJSONObject() {
		JSONObject obj = new JSONObject();
		try {
			obj.put("id", id);
			obj.put("name", name);
			obj.put("iconRes", iconRes);
		    obj.put("value", value);
		} catch (JSONException e) {
		}
		return obj;
	}
	
	@Override
	public String toString() {
		return toJSONObject().toString();
	}
}
