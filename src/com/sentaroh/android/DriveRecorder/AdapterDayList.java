package com.sentaroh.android.DriveRecorder;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class AdapterDayList extends ArrayAdapter<DayListItem> {
	private Context c;
	private int id;
	private ArrayList<DayListItem>items;
	
	public AdapterDayList(Context context, 
			int textViewResourceId, ArrayList<DayListItem> objects) {
		super(context, textViewResourceId, objects);
		c = context;
		id = textViewResourceId;
		items=objects;
	};
	
	@Override
	final public int getCount() {
		return items.size();
	}

	@Override
	final public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		
        View v = convertView;
        if (v == null) {
            LayoutInflater vi = (LayoutInflater)c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(id, null);
            holder=new ViewHolder();
            holder.tv_itemname= (TextView) v.findViewById(R.id.day_list_item_name);
            holder.tv_count= (TextView) v.findViewById(R.id.day_list_item_count);
            v.setTag(holder);
        } else {
        	holder= (ViewHolder)v.getTag();
        }
        final DayListItem o = items.get(position);
    	holder.tv_itemname.setText(o.day);
    	holder.tv_count.setText(o.no_of_file);
        return v;
	};

	class ViewHolder {
		TextView tv_itemname, tv_count;
	}
}

class DayListItem {
	public String day="";
	public String no_of_file="";
}