package com.cheogram.android;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ListAdapter;

public class Util {
	public static void justifyListViewHeightBasedOnChildren (ListView listView) {
		ListAdapter adapter = listView.getAdapter();

		if (adapter == null) {
			return;
		}
		ViewGroup vg = listView;
		int totalHeight = 0;
		for (int i = 0; i < adapter.getCount(); i++) {
			View listItem = adapter.getView(i, null, vg);
			listItem.measure(0, 0);
			totalHeight += listItem.getMeasuredHeight();
		}

		ViewGroup.LayoutParams par = listView.getLayoutParams();
		par.height = totalHeight + (listView.getDividerHeight() * (adapter.getCount() - 1));
		listView.setLayoutParams(par);
		listView.requestLayout();
	}
}
