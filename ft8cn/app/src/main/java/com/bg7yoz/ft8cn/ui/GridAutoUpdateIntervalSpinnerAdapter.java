package com.bg7yoz.ft8cn.ui;
/**
 * Grid auto-update interval spinner adapter
 * @author BGY70Z
 * @date 2025-10-24
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.bg7yoz.ft8cn.R;

public class GridAutoUpdateIntervalSpinnerAdapter extends BaseAdapter {
    private final Context mContext;
    private final int[] intervals = {0, 1, 2, 3, 4, 5};
    private String[] intervalsStr;
    
    public GridAutoUpdateIntervalSpinnerAdapter(Context context) {
        mContext = context;
        intervalsStr = new String[]{
            mContext.getString(R.string.grid_autoupdate_disabled),
            mContext.getString(R.string.grid_autoupdate_1min),
            mContext.getString(R.string.grid_autoupdate_2min),
            mContext.getString(R.string.grid_autoupdate_3min),
            mContext.getString(R.string.grid_autoupdate_4min),
            mContext.getString(R.string.grid_autoupdate_5min)
        };
    }

    @Override
    public int getCount() {
        return intervals.length;
    }

    @Override
    public Object getItem(int i) {
        return intervals[i];
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @SuppressLint({"ViewHolder", "InflateParams"})
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        LayoutInflater layoutInflater = LayoutInflater.from(mContext);
        view = layoutInflater.inflate(R.layout.grid_auto_update_interval_spinner_item, null);
        if (view != null) {
            TextView textView = view.findViewById(R.id.gridAutoUpdateIntervalItemTextView);
            textView.setText(intervalsStr[i]);
        }
        return view;
    }
    
    public int getPosition(int value) {
        for (int j = 0; j < intervals.length; j++) {
            if (intervals[j] == value) {
                return j;
            }
        }
        return 0; // default to "Disabled"
    }
    
    public int getValue(int position) {
        return intervals[position];
    }
}
