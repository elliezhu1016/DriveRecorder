package com.sentaroh.android.DriveRecorder;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class AdapterFileList extends ArrayAdapter<FileListItem> {
	private Context c;
	private int id;
	private ArrayList<FileListItem>items;
	
	public AdapterFileList(Context context, 
			int textViewResourceId, ArrayList<FileListItem> objects) {
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
            holder.tv_itemname= (TextView) v.findViewById(R.id.file_list_item_name);
            holder.tv_file_size= (TextView) v.findViewById(R.id.file_list_item_size);
            holder.tv_duration= (TextView) v.findViewById(R.id.file_list_item_duration);
            holder.iv_thumnail=(ImageView) v.findViewById(R.id.file_list_thumnail);
            v.setTag(holder);
        } else {
        	holder= (ViewHolder)v.getTag();
        }
        final FileListItem o = items.get(position);
    	holder.tv_itemname.setText(o.file_name);
    	if (holder.tv_duration!=null) holder.tv_duration.setText(o.duration);
    	holder.tv_file_size.setText(o.file_size);
    	holder.iv_thumnail.setImageBitmap(o.thumbnail);
        return v;
	};

	class ViewHolder {
		TextView tv_itemname, tv_file_size, tv_duration;
		ImageView iv_thumnail;
	}
}

class FileListItem {
	public String file_name="";
	public String duration="00:00";
	public String file_size="";
	public Bitmap thumbnail=null;
}