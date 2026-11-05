package de.niendo.ImapNotes3;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/** SwipeRefreshLayout that only reacts when the wrapped ListView is at the top. */
public class ListSwipeRefreshLayout extends SwipeRefreshLayout {
    @Nullable
    private ListView listView;

    public ListSwipeRefreshLayout(@NonNull Context context) {
        super(context);
    }

    public ListSwipeRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setListView(@Nullable ListView view) {
        listView = view;
    }

    @Override
    public boolean canChildScrollUp() {
        return listView != null && listView.canScrollVertically(-1);
    }
}
