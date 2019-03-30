@file:Suppress("NOTHING_TO_INLINE")

package me.dmdev.rxpm.widget

import android.support.design.widget.TextInputLayout
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.widget.EditText
import com.jakewharton.rxbinding2.widget.textChanges
import io.reactivex.android.schedulers.AndroidSchedulers
import me.dmdev.rxpm.AndroidPmView
import me.dmdev.rxpm.PmView
import me.dmdev.rxpm.PresentationModel

/**
 * Helps to bind a group of properties of an input field widget to a [presentation model][PresentationModel]
 * and also breaks the loop of two-way data binding to make the work with the input easier.
 *
 * You can bind this to an [EditText] or an [TextInputLayout] using the familiar `bindTo` methods
 * in the [AndroidPmView].
 *
 * Instantiate this using the [inputControl] extension function of the presentation model.
 *
 * @see CheckControl
 * @see DialogControl
 */
class InputControl internal constructor(
    initialText: String,
    private val formatter: ((text: String) -> String)?,
    private val hideErrorOnUserInput: Boolean = true
) : PresentationModel() {

    /**
     * The input field text [state][PresentationModel.State].
     */
    val text = State(initialText)

    /**
     * The input field error [state][PresentationModel.State].
     */
    val error = State<String>()

    /**
     * The input field text changes [events][PresentationModel.Action].
     */
    val textChanges = Action<String>()

    override fun onCreate() {

        if (formatter != null) {
            textChanges.observable
                .filter { it != text.value }
                .map { formatter.invoke(it) }
                .subscribe {
                    text.consumer.accept(it)
                    if (hideErrorOnUserInput) error.consumer.accept("")
                }
                .untilDestroy()
        }
    }
}

/**
 * Creates the [InputControl].
 *
 * @param initialText initial text of the input field.
 * @param formatter formats the user input. The default does nothing.
 * @param hideErrorOnUserInput hide the error if user entered something.
 */
fun PresentationModel.inputControl(
    initialText: String = "",
    formatter: ((text: String) -> String)? = { it },
    hideErrorOnUserInput: Boolean = true
): InputControl {
    return InputControl(initialText, formatter, hideErrorOnUserInput).apply {
        attachToParent(this@inputControl)
    }
}

/**
 * Binds the [InputControl] to the [TextInputLayout][textInputLayout], use it ONLY in [PmView.onBindPresentationModel].
 */
inline infix fun InputControl.bindTo(textInputLayout: TextInputLayout) {

    val edit = textInputLayout.editText!!

    bindTo(edit)

    error.observable
        .subscribe { error ->
            textInputLayout.error = if (error.isEmpty()) null else error
        }
        .untilUnbind()
}

/**
 * Binds the [InputControl] to the [EditText][editText], use it ONLY in [PmView.onBindPresentationModel].
 *
 * @since 2.0
 */
inline infix fun InputControl.bindTo(editText: EditText) {

    var editing = false

    text.observable
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {
            val editable = editText.text
            if (!it!!.contentEquals(editable)) {
                editing = true
                if (editable is Spanned) {
                    val ss = SpannableString(it)
                    TextUtils.copySpansFrom(editable, 0, ss.length, null, ss, 0)
                    editable.replace(0, editable.length, ss)
                } else {
                    editable.replace(0, editable.length, it)
                }
                editing = false
            }
        }
        .untilUnbind()

    editText.textChanges()
        .skipInitialValue()
        .filter { !editing }
        .map { it.toString() }
        .subscribe(textChanges.consumer)
        .untilUnbind()
}