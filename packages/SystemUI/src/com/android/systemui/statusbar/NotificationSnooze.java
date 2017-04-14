package com.android.systemui.statusbar;
/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

import java.util.ArrayList;
import java.util.List;

import com.android.systemui.plugins.statusbar.NotificationSwipeActionHelper;
import com.android.systemui.plugins.statusbar.NotificationSwipeActionHelper.SnoozeOption;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.service.notification.SnoozeCriterion;
import android.service.notification.StatusBarNotification;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.systemui.R;

public class NotificationSnooze extends LinearLayout
        implements NotificationGuts.GutsContent, View.OnClickListener {

    private static final int MAX_ASSISTANT_SUGGESTIONS = 2;
    private NotificationGuts mGutsContainer;
    private NotificationSwipeActionHelper mSnoozeListener;
    private StatusBarNotification mSbn;

    private TextView mSelectedOptionText;
    private TextView mUndoButton;
    private ViewGroup mSnoozeOptionView;
    private List<SnoozeOption> mSnoozeOptions;
    private boolean mSnoozing;

    private SnoozeOption mSelectedOption;

    public NotificationSnooze(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Create the different options based on list
        mSnoozeOptions = getDefaultSnoozeOptions();
        createOptionViews();

        // Snackbar
        mSelectedOptionText = findViewById(R.id.snooze_option_default);
        mSelectedOptionText.setOnClickListener(this);
        mUndoButton = findViewById(R.id.undo);
        mUndoButton.setOnClickListener(this);

        // Default to first option in list
        setSelected(mSnoozeOptions.get(0));
    }

    public void setSnoozeOptions(final List<SnoozeCriterion> snoozeList) {
        if (snoozeList == null) {
            return;
        }
        mSnoozeOptions.clear();
        mSnoozeOptions = getDefaultSnoozeOptions();
        final int count = Math.min(MAX_ASSISTANT_SUGGESTIONS, snoozeList.size());
        for (int i = 0; i < count; i++) {
            SnoozeCriterion sc = snoozeList.get(i);
            mSnoozeOptions.add(new SnoozeOption(sc, 0, sc.getExplanation(), sc.getConfirmation()));
        }
        createOptionViews();
    }

    private ArrayList<SnoozeOption> getDefaultSnoozeOptions() {
        ArrayList<SnoozeOption> options = new ArrayList<>();
        options.add(createOption(R.string.snooze_option_15_min, 15));
        options.add(createOption(R.string.snooze_option_30_min, 30));
        options.add(createOption(R.string.snooze_option_1_hour, 60));
        return options;
    }

    private SnoozeOption createOption(int descriptionResId, int minutes) {
        Resources res = getResources();
        String resultText = String.format(
                res.getString(R.string.snoozed_for_time), res.getString(descriptionResId));
        return new SnoozeOption(null, minutes, res.getString(descriptionResId), resultText);
    }

    private void createOptionViews() {
        mSnoozeOptionView = findViewById(R.id.snooze_options);
        mSnoozeOptionView.removeAllViews();
        mSnoozeOptionView.setVisibility(View.GONE);
        final Resources res = getResources();
        final int textSize = res.getDimensionPixelSize(R.dimen.snooze_option_text_size);
        final int p = res.getDimensionPixelSize(R.dimen.snooze_option_padding);

        // Add all the options
        for (int i = 0; i < mSnoozeOptions.size(); i++) {
            SnoozeOption option = mSnoozeOptions.get(i);
            TextView tv = new TextView(getContext());
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
            tv.setPadding(p, p, p, p);
            mSnoozeOptionView.addView(tv);
            tv.setText(option.description);
            tv.setTag(option);
            tv.setOnClickListener(this);
        }

        // Add the undo option as final item
        TextView tv = new TextView(getContext());
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        tv.setPadding(p, p, p, p);
        mSnoozeOptionView.addView(tv);
        tv.setText(R.string.snooze_option_dont_snooze);
        tv.setOnClickListener(this);
    }

    private void showSnoozeOptions(boolean show) {
        mSelectedOptionText.setVisibility(show ? View.GONE : View.VISIBLE);
        mUndoButton.setVisibility(show ? View.GONE : View.VISIBLE);
        mSnoozeOptionView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setSelected(SnoozeOption option) {
        mSelectedOption = option;
        mSelectedOptionText.setText(option.confirmation);
        showSnoozeOptions(false);
    }

    @Override
    public void onClick(View v) {
        if (mGutsContainer != null) {
            mGutsContainer.resetFalsingCheck();
        }
        final int id = v.getId();
        final SnoozeOption tag = (SnoozeOption) v.getTag();
        if (tag != null) {
            setSelected(tag);
        } else if (id == R.id.snooze_option_default) {
            // Show more snooze options
            showSnoozeOptions(true);
        } else {
            undoSnooze();
        }
    }

    private void undoSnooze() {
        mSelectedOption = null;
        mGutsContainer.closeControls(-1 /* x */, -1 /* y */, true /* notify */);
    }

    @Override
    public boolean willBeRemoved() {
        return mSnoozing;
    }

    @Override
    public View getContentView() {
        return this;
    }

    public void setStatusBarNotification(StatusBarNotification sbn) {
        mSbn = sbn;
    }

    @Override
    public void setGutsParent(NotificationGuts guts) {
        mGutsContainer = guts;
    }

    public void setSnoozeListener(NotificationSwipeActionHelper listener) {
        mSnoozeListener = listener;
    }

    @Override
    public boolean handleCloseControls(boolean save) {
        // When snooze is closed (i.e. there was interaction outside of the notification)
        // then we commit the snooze action.
        if (mSnoozeListener != null && mSelectedOption != null) {
            mSnoozing = true;
            mSnoozeListener.snooze(mSbn, mSelectedOption);
            return true;
        } else {
            // Reset the view once it's closed
            setSelected(mSnoozeOptions.get(0));
            showSnoozeOptions(false);
        }
        return false;
    }
}
