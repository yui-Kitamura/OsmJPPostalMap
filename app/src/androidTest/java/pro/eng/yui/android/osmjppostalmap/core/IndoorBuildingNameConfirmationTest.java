package pro.eng.yui.android.osmjppostalmap.core;

import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository;
import pro.eng.yui.android.osmjppostalmap.ui.EditPoiActivity;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class IndoorBuildingNameConfirmationTest {

    @Test
    public void indoorCheckboxIsIndependentFromShapeSelection() {
        try (ActivityScenario<EditPoiActivity> scenario = launchEditor()) {
            scenario.onActivity(activity -> {
                CheckBox indoor = activity.findViewById(R.id.check_indoor);
                RadioGroup shape = activity.findViewById(R.id.edit_radio_shape);
                assertTrue(indoor.getVisibility() == View.VISIBLE);
                assertFalse(indoor.isChecked());

                indoor.performClick();
                assertTrue(indoor.isChecked());
                shape.check(R.id.edit_shape_box);
                assertTrue(indoor.isChecked());
                assertTrue(shape.getCheckedRadioButtonId() == R.id.edit_shape_box);

                indoor.performClick();
                assertFalse(indoor.isChecked());
            });
        }
    }

    @Test
    public void returningToInputDoesNotSaveAndFocusesAddressEditButton() {
        AtomicBoolean saved = new AtomicBoolean(false);
        try (ActivityScenario<EditPoiActivity> scenario = launchEditor()) {
            scenario.onActivity(activity -> {
                View addressEditButton = activity.findViewById(R.id.btn_address_edit);
                assertTrue(addressEditButton.isFocusableInTouchMode());
                IndoorBuildingNameConfirmation.show(activity, addressEditButton, () -> saved.set(true));
            });

            onView(withText(R.string.msg_building_name_required)).check(
                    androidx.test.espresso.assertion.ViewAssertions.matches(
                            androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
            onView(withText(R.string.btn_return_to_input)).perform(click());

            scenario.onActivity(activity -> assertTrue(activity.findViewById(R.id.btn_address_edit).hasFocus()));
            assertFalse(saved.get());
        }
    }

    @Test
    public void addressEditDialogOpensOnFirstTapAndCanBeReopened() {
        try (ActivityScenario<EditPoiActivity> scenario = launchEditor()) {
            scenario.onActivity(activity -> {
                View otherInput = activity.findViewById(R.id.edit_special_note_value);
                assertTrue(otherInput.requestFocus());
                assertTrue(otherInput.hasFocus());
            });

            onView(withId(R.id.btn_address_edit)).perform(scrollTo(), click());
            onView(withText(R.string.title_address_edit)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> assertTrue(activity.findViewById(R.id.btn_address_edit).hasFocus()));

            onView(withText(R.string.btn_close)).perform(click());
            onView(withId(R.id.btn_address_edit)).perform(scrollTo(), click());
            onView(withText(R.string.title_address_edit)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void savingAnywayInvokesSaveCallback() {
        AtomicBoolean saved = new AtomicBoolean(false);
        try (ActivityScenario<EditPoiActivity> scenario = launchEditor()) {
            scenario.onActivity(activity -> IndoorBuildingNameConfirmation.show(
                    activity, activity.findViewById(R.id.btn_address_edit), () -> saved.set(true)));

            onView(withText(R.string.btn_save_without_building_name)).perform(click());

            assertTrue(saved.get());
        }
    }

    private ActivityScenario<EditPoiActivity> launchEditor() {
        ActivityScenario<EditPoiActivity> scenario = ActivityScenario.launch(EditPoiActivity.class);
        AtomicBoolean needsNoteChoice = new AtomicBoolean(false);
        scenario.onActivity(activity -> needsNoteChoice.set(new AuthRepository(activity).getAccessToken() == null));
        if (needsNoteChoice.get()) {
            onView(withText("このまま地図メモを残す")).perform(click());
        }
        return scenario;
    }
}