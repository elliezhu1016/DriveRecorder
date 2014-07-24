package com.sentaroh.android.DriveRecorder.Log;

/*
The MIT License (MIT)
Copyright (c) 2011-2013 Sentaroh

Permission is hereby granted, free of charge, to any person obtaining a copy of 
this software and associated documentation files (the "Software"), to deal 
in the Software without restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
and to permit persons to whom the Software is furnished to do so, subject to 
the following conditions:

The above copyright notice and this permission notice shall be included in all copies or 
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE 
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, 
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

*/

import java.util.ArrayList;

import com.sentaroh.android.DriveRecorder.R;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

public class LogFileListAdapter extends BaseAdapter{

	private ArrayList<LogFileListItem>log_list=null;
	private int textViewResourceId=0;
	private Context c;
	
	public LogFileListAdapter(Context context, int textViewResourceId,
			ArrayList<LogFileListItem> objects) {
		c=context;
		log_list=objects;
		this.textViewResourceId=textViewResourceId;
	}
	
	public void replaceDataList(ArrayList<LogFileListItem> dl) {
		log_list=dl;
		notifyDataSetChanged();
	}
	
	@Override
	public int getCount() {
		return log_list.size();
	}

	@Override
	public LogFileListItem getItem(int pos) {
		return log_list.get(pos);
	}

	@Override
	public long getItemId(int arg0) {
		return arg0;
	}

	@Override
    final public View getView(final int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
        View v = convertView;
        if (v == null) {
            LayoutInflater vi = (LayoutInflater)c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(textViewResourceId, null);
            holder=new ViewHolder();
            holder.cb_select=(CheckBox)v.findViewById(R.id.log_file_list_item_checkbox);
            holder.tv_log_file_name=(TextView)v.findViewById(R.id.log_file_list_item_log_name);
            holder.tv_log_file_size=(TextView)v.findViewById(R.id.log_file_list_item_log_size);
            holder.tv_log_file_date=(TextView)v.findViewById(R.id.log_file_list_item_log_last_modified_date);
            holder.tv_log_file_time=(TextView)v.findViewById(R.id.log_file_list_item_log_last_modified_time);
            v.setTag(holder);
        } else {
        	holder= (ViewHolder)v.getTag();
        }
        final LogFileListItem o = getItem(position);
        if (o.log_file_name!=null) {
        	if (o.isCurrentLogFile) holder.tv_log_file_name.setTextColor(Color.RED);
        	else holder.tv_log_file_name.setTextColor(Color.WHITE);
    		holder.tv_log_file_name.setText(o.log_file_name);
    		holder.tv_log_file_size.setText(o.log_file_size);
    		holder.tv_log_file_date.setText(o.log_file_last_modified_date);
    		holder.tv_log_file_time.setText(o.log_file_last_modified_time);
         	holder.cb_select.setOnCheckedChangeListener(new OnCheckedChangeListener() {
    			@Override
    			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    				o.isChecked=isChecked;
    			}
    		});
         	holder.cb_select.setChecked(getItem(position).isChecked);
        } else {
    		holder.tv_log_file_name.setText("No log files");
    		holder.cb_select.setVisibility(TextView.GONE);
    		holder.tv_log_file_size.setVisibility(TextView.GONE);
    		holder.tv_log_file_date.setVisibility(TextView.GONE);
    		holder.tv_log_file_time.setVisibility(TextView.GONE);
        }
        return v;
	};

	static class ViewHolder {
		CheckBox cb_select;
		TextView tv_log_file_name,tv_log_file_size,tv_log_file_date, tv_log_file_time;
	}
}
