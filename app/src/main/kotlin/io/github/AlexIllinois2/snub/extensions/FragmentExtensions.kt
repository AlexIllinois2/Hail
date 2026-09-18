package io.github.AlexIllinois2.snub.extensions

import androidx.fragment.app.Fragment

val Fragment.isLandscape get() = requireContext().isLandscape
val Fragment.isRtl get() = requireContext().isRtl