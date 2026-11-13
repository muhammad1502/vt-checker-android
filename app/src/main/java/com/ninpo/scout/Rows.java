package com.ninpo.scout;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

/** Fills a list with rows separated by hairlines (the app's list-card pattern). */
final class Rows {

    private Rows() {}

    /** Numbered steps (item_step): each row is {title, body}. */
    static void addSteps(@NonNull LinearLayout parent, @NonNull int[][] rows) {
        add(parent, R.layout.item_step, rows, true);
    }

    /** Name and meta rows (item_row): each row is {name, meta}. */
    static void addRows(@NonNull LinearLayout parent, @NonNull int[][] rows) {
        add(parent, R.layout.item_row, rows, false);
    }

    private static void add(LinearLayout parent, int layout, int[][] rows, boolean numbered) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        int hairline = context.getResources().getDimensionPixelSize(R.dimen.hairline);
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                View rule = new View(context);
                rule.setBackgroundResource(R.drawable.divider);
                parent.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, hairline));
            }
            View row = inflater.inflate(layout, parent, false);
            if (numbered) {
                ((TextView) row.findViewById(R.id.step_number)).setText(String.valueOf(i + 1));
                ((TextView) row.findViewById(R.id.step_title)).setText(rows[i][0]);
                ((TextView) row.findViewById(R.id.step_body)).setText(rows[i][1]);
            } else {
                ((TextView) row.findViewById(R.id.row_title)).setText(rows[i][0]);
                ((TextView) row.findViewById(R.id.row_meta)).setText(rows[i][1]);
            }
            if (i == rows.length - 1) {
                row.setPadding(row.getPaddingLeft(), row.getPaddingTop(), row.getPaddingRight(), 0);
            }
            parent.addView(row);
        }
    }
}
