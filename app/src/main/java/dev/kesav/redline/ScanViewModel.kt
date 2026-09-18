package dev.kesav.redline

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface ScanState {
    data object Editing : ScanState
    data class Scanned(val clauses: List<Clause>) : ScanState
}

data class ScanUi(
    val text: String = "",
    val state: ScanState = ScanState.Editing,
)

class ScanViewModel : ViewModel() {

    private val _ui = MutableStateFlow(ScanUi())
    val ui: StateFlow<ScanUi> = _ui.asStateFlow()

    fun seed(text: String) {
        if (text.isBlank() || _ui.value.text.isNotEmpty()) return
        _ui.update { it.copy(text = text) }
    }

    fun edit(text: String) {
        _ui.update { it.copy(text = text, state = ScanState.Editing) }
    }

    fun scan() {
        val clauses = ClauseSplitter.split(_ui.value.text)
        _ui.update { it.copy(state = ScanState.Scanned(clauses)) }
    }

    fun back() {
        _ui.update { it.copy(state = ScanState.Editing) }
    }
}
