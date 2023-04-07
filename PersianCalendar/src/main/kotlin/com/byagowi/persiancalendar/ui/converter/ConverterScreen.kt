package com.byagowi.persiancalendar.ui.converter

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.databinding.ConverterScreenBinding
import com.byagowi.persiancalendar.databinding.ConverterSpinnerBinding
import com.byagowi.persiancalendar.entities.Jdn
import com.byagowi.persiancalendar.global.enabledCalendars
import com.byagowi.persiancalendar.global.mainCalendar
import com.byagowi.persiancalendar.global.spacedComma
import com.byagowi.persiancalendar.ui.utils.getCompatDrawable
import com.byagowi.persiancalendar.ui.utils.layoutInflater
import com.byagowi.persiancalendar.ui.utils.onClick
import com.byagowi.persiancalendar.ui.utils.setupLayoutTransition
import com.byagowi.persiancalendar.ui.utils.setupMenuNavigation
import com.byagowi.persiancalendar.ui.utils.shareText
import com.byagowi.persiancalendar.utils.calculateDaysDifference
import com.byagowi.persiancalendar.utils.dateStringOfOtherCalendars
import com.byagowi.persiancalendar.utils.dayTitleSummary
import io.github.persiancalendar.calculator.eval
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ConverterScreen : Fragment(R.layout.converter_screen) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = ConverterScreenBinding.bind(view)

        val viewModel by viewModels<ConverterViewModel>()
        binding.dayPickerView.changeCalendarType(viewModel.calendar.value)

        val spinner = run {
            val toolbarContext = binding.appBar.toolbar.context
            val spinnerBinding = ConverterSpinnerBinding.inflate(toolbarContext.layoutInflater)
            binding.appBar.toolbar.addView(spinnerBinding.root)
            spinnerBinding.spinner
        }
        spinner.adapter = ArrayAdapter(
            spinner.context, R.layout.toolbar_dropdown_item,
            enumValues<ConverterScreenMode>().map { it.title }.map(spinner.context::getString)
        )
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) = viewModel.changeScreenMode(ConverterScreenMode.fromPosition(position))
        }
        spinner.setSelection(viewModel.screenMode.value.ordinal)

        binding.appBar.toolbar.setupMenuNavigation()

        binding.calendarsView.post { // is in 'post' as otherwise will show ann empty circular indicator
            binding.calendarsView.toggle()
        }
        binding.calendarsView.hideMoreIcon()

        val todayJdn = Jdn.today()

        val todayButton = binding.appBar.toolbar.menu.add(R.string.return_to_today).also {
            it.icon =
                binding.appBar.toolbar.context.getCompatDrawable(R.drawable.ic_restore_modified)
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            it.onClick {
                binding.dayPickerView.jdn = todayJdn
                binding.secondDayPickerView.jdn = todayJdn
            }
        }

        binding.appBar.toolbar.menu.add(R.string.share).also { menu ->
            menu.icon =
                binding.appBar.toolbar.context.getCompatDrawable(R.drawable.ic_baseline_share)
            menu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }.onClick {
            val jdn = binding.dayPickerView.jdn
            activity?.shareText(
                if (viewModel.screenMode.value == ConverterScreenMode.Converter) listOf(
                    dayTitleSummary(jdn, jdn.toCalendar(mainCalendar)),
                    getString(R.string.equivalent_to),
                    dateStringOfOtherCalendars(jdn, spacedComma)
                ).joinToString(" ") else binding.resultText.text.toString()
            )
        }

        binding.secondDayPickerView.jdn = viewModel.secondSelectedDate.value
        binding.secondDayPickerView.turnToSecondaryDatePicker()
        binding.dayPickerView.selectedDayListener = viewModel::changeSelectedDate
        binding.dayPickerView.selectedCalendarListener = viewModel::changeCalendar
        binding.dayPickerView.jdn = viewModel.selectedDate.value
        binding.secondDayPickerView.selectedDayListener = viewModel::changeSecondSelectedDate
        binding.inputText.setText(viewModel.inputText.value)
        binding.inputText.doOnTextChanged { text, _, _, _ ->
            viewModel.changeCalculatorInput(text?.toString() ?: "")
        }

        binding.converterRoot.setupLayoutTransition()

        // Setup view model change listeners
        // https://developer.android.com/topic/libraries/architecture/coroutines#lifecycle-aware
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.updateEvent.collectLatest {
                        when (viewModel.screenMode.value) {
                            ConverterScreenMode.Converter -> {
                                val selectedCalendarType = viewModel.calendar.value
                                binding.calendarsView.showCalendars(
                                    viewModel.selectedDate.value,
                                    selectedCalendarType,
                                    enabledCalendars - selectedCalendarType
                                )
                            }
                            ConverterScreenMode.Distance -> {
                                binding.resultText.textDirection = View.TEXT_DIRECTION_INHERIT
                                binding.resultText.text = calculateDaysDifference(
                                    resources, viewModel.selectedDate.value,
                                    viewModel.secondSelectedDate.value, viewModel.calendar.value
                                )
                            }
                            ConverterScreenMode.Calculator -> {
                                binding.resultText.textDirection = View.TEXT_DIRECTION_LTR
                                binding.resultText.text = runCatching {
                                    // running this inside a runCatching block is absolutely important
                                    eval(binding.inputText.text?.toString() ?: "")
                                }.getOrElse { it.message }
                            }
                        }
                    }
                }
                launch {
                    viewModel.calendarChangeEvent.collectLatest {
                        if (viewModel.screenMode.value == ConverterScreenMode.Distance)
                            binding.secondDayPickerView.changeCalendarType(it)
                    }
                }
                launch {
                    viewModel.todayButtonVisibilityEvent
                        .distinctUntilChanged()
                        .collectLatest(todayButton::setVisible)
                }
                launch {
                    viewModel.screenModeChangeEvent.collectLatest {
                        when (viewModel.screenMode.value) {
                            ConverterScreenMode.Converter -> {
                                binding.inputTextWrapper.isVisible = false
                                binding.secondDayPickerView.isVisible = false
                                binding.dayPickerView.isVisible = true
                                binding.resultText.isVisible = false
                                binding.calendarsView.isVisible = true
                                binding.resultCard.isVisible = true
                            }
                            ConverterScreenMode.Distance -> {
                                binding.secondDayPickerView.changeCalendarType(viewModel.calendar.value)

                                binding.inputTextWrapper.isVisible = false
                                binding.dayPickerView.isVisible = true
                                binding.secondDayPickerView.isVisible = true
                                binding.resultText.isVisible = true
                                binding.calendarsView.isVisible = false
                                binding.resultCard.isVisible = false
                            }
                            ConverterScreenMode.Calculator -> {
                                binding.inputTextWrapper.isVisible = true
                                binding.dayPickerView.isVisible = false
                                binding.secondDayPickerView.isVisible = false
                                binding.resultText.isVisible = true
                                binding.calendarsView.isVisible = false
                                binding.resultCard.isVisible = false
                            }
                        }
                    }
                }
            }
        }
    }
}
