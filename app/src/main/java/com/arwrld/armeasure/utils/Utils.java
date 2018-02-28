package com.arwrld.armeasure.utils;

import android.content.Context;
import android.util.TypedValue;

/**
 * Created by davidhodge on 1/26/18.
 */

public class Utils {

    public static int getSizeFromDistance(Context mContext, double distance) {
        int size = Utils.dpToPx(mContext, 50);
        if (distance <= 75) {
            size = Utils.dpToPx(mContext, 50);
        } else if (distance > 75 && distance <= 150) {
            size = Utils.dpToPx(mContext, 40);
        } else if (distance > 150 && distance <= 250) {
            size = Utils.dpToPx(mContext, 30);
        } else if (distance > 250 && distance > 1000) {
            size = Utils.dpToPx(mContext, 20);
        } else {
            size = Utils.dpToPx(mContext, 12);
        }
        return size;
    }

    public static int dpToPx(Context context, int dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dp, context.getResources().getDisplayMetrics()));
    }

}
