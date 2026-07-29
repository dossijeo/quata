package com.quata.feature.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIAccessibilityIdentificationProtocol
import platform.UIKit.UITextAutocapitalizationType
import platform.UIKit.UITextAutocorrectionType
import platform.UIKit.UITextContentTypePassword
import platform.UIKit.UITextContentTypeTelephoneNumber
import platform.UIKit.UIKeyboardTypeDefault
import platform.UIKit.UIKeyboardTypePhonePad
import platform.UIKit.UITextField
import platform.UIKit.UITextFieldDelegateProtocol
import platform.darwin.NSObject

/**
 * Native accessibility and keyboard bridge for the real iOS Auth fields.
 *
 * The transparent UIKit view is deliberately layered over the shared Compose field rather than
 * replacing it: Compose remains the only visual renderer and the callback still updates the
 * existing [LoginViewModel]. UIKit is used solely for the platform contract XCTest requires:
 * a focus-capable text input. The surrounding Compose nodes retain the stable `auth.*` test
 * tags, while the UIKit field exposes its locale-owned placeholder as its native XCTest label.
 */
@Composable
internal fun IosNativeAuthAccessibilityInput(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    identifier: String,
    label: String,
    password: Boolean,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            IosNativeAuthTextField(
                label = label,
                password = password,
                identifier = identifier,
            )
        },
        update = { input ->
            input.onValueChange = onValueChange
            input.onFocusChanged = onFocusChanged
            if (input.text != value) input.text = value
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalForeignApi::class)
private class IosNativeAuthTextField(
    label: String,
    password: Boolean,
    identifier: String,
) : UITextField(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var onValueChange: (String) -> Unit = {}
    var onFocusChanged: (Boolean) -> Unit = {}
    private val editingDelegate = IosNativeAuthTextFieldDelegate(
        onChanged = { text -> onValueChange(text) },
        onFocusChanged = { focused -> onFocusChanged(focused) },
    )

    init {
        placeholder = label
        (this as UIAccessibilityIdentificationProtocol).accessibilityIdentifier = identifier
        setSecureTextEntry(password)
        setKeyboardType(if (password) UIKeyboardTypeDefault else UIKeyboardTypePhonePad)
        textContentType = if (password) UITextContentTypePassword else UITextContentTypeTelephoneNumber
        autocorrectionType = UITextAutocorrectionType.UITextAutocorrectionTypeNo
        autocapitalizationType = UITextAutocapitalizationType.UITextAutocapitalizationTypeNone
        setBackgroundColor(UIColor.clearColor)
        setTextColor(UIColor.clearColor)
        setTintColor(UIColor.clearColor)
        setDelegate(editingDelegate)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosNativeAuthTextFieldDelegate(
    private val onChanged: (String) -> Unit,
    private val onFocusChanged: (Boolean) -> Unit,
) : NSObject(), UITextFieldDelegateProtocol {
    override fun textFieldDidChangeSelection(textField: UITextField) {
        onChanged(textField.text.orEmpty())
    }

    override fun textFieldDidBeginEditing(textField: UITextField) {
        onFocusChanged(true)
    }

    override fun textFieldDidEndEditing(textField: UITextField) {
        onFocusChanged(false)
    }
}
