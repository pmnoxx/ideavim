package com.maddyhome.idea.vim.vimscript.model.options.helpers

import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.editor.Editor
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.helper.hasBlockOrUnderscoreCaret
import com.maddyhome.idea.vim.helper.hasVisualSelection
import com.maddyhome.idea.vim.helper.mode
import com.maddyhome.idea.vim.helper.subMode
import com.maddyhome.idea.vim.listener.SelectionVimListenerSuppressor
import com.maddyhome.idea.vim.option.IdeaRefactorMode
import com.maddyhome.idea.vim.vimscript.model.datatypes.VimString
import com.maddyhome.idea.vim.vimscript.services.OptionService

object IdeaRefactorModeHelper {

  fun keepMode(): Boolean = (VimPlugin.getOptionService().getOptionValue(OptionService.Scope.GLOBAL, "idearefactormode") as VimString).value == IdeaRefactorMode.keep
  fun selectMode(): Boolean = (VimPlugin.getOptionService().getOptionValue(OptionService.Scope.GLOBAL, "idearefactormode") as VimString).value == IdeaRefactorMode.select

  fun correctSelection(editor: Editor) {
    val action: () -> Unit = {
      if (!editor.mode.hasVisualSelection && editor.selectionModel.hasSelection()) {
        SelectionVimListenerSuppressor.lock().use {
          editor.selectionModel.removeSelection()
        }
      }
      if (editor.mode.hasVisualSelection && editor.selectionModel.hasSelection()) {
        val autodetectedSubmode = VimPlugin.getVisualMotion().autodetectVisualSubmode(editor)
        if (editor.subMode != autodetectedSubmode) {
          // Update the submode
          editor.subMode = autodetectedSubmode
        }
      }

      if (editor.hasBlockOrUnderscoreCaret()) {
        TemplateManagerImpl.getTemplateState(editor)?.currentVariableRange?.let { segmentRange ->
          if (!segmentRange.isEmpty && segmentRange.endOffset == editor.caretModel.offset && editor.caretModel.offset != 0) {
            editor.caretModel.moveToOffset(editor.caretModel.offset - 1)
          }
        }
      }
    }

    val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl
    if (lookup != null) {
      val selStart = editor.selectionModel.selectionStart
      val selEnd = editor.selectionModel.selectionEnd
      lookup.performGuardedChange(action)
      lookup.addLookupListener(object : LookupListener {
        override fun beforeItemSelected(event: LookupEvent): Boolean {
          // FIXME: 01.11.2019 Nasty workaround because of problems in IJ platform
          //   Lookup replaces selected text and not the template itself. So, if there is no selection
          //   in the template, lookup value will not replace the template, but just insert value on the caret position
          lookup.performGuardedChange { editor.selectionModel.setSelection(selStart, selEnd) }
          lookup.removeLookupListener(this)
          return true
        }
      })
    } else {
      action()
    }
  }
}
