/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2021 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.vimscript.model.expressions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.ex.ExException
import com.maddyhome.idea.vim.vimscript.model.Executable
import com.maddyhome.idea.vim.vimscript.model.datatypes.VimDataType
import com.maddyhome.idea.vim.vimscript.model.datatypes.VimFuncref
import com.maddyhome.idea.vim.vimscript.model.functions.DefinedFunctionHandler
import com.maddyhome.idea.vim.vimscript.model.statements.FunctionFlag
import com.maddyhome.idea.vim.vimscript.services.FunctionStorage

data class FunctionCallExpression(val scope: Scope?, val functionName: CurlyBracesName, val arguments: MutableList<Expression>) :
  Expression() {
  constructor(scope: Scope?, functionName: String, arguments: MutableList<Expression>) :
    this(scope, CurlyBracesName(listOf(SimpleExpression(functionName))), arguments)

  override fun evaluate(editor: Editor, context: DataContext, parent: Executable): VimDataType {
    val handler = FunctionStorage.getFunctionHandlerOrNull(scope, functionName.evaluate(editor, context, parent).value, parent)
    if (handler != null) {
      if (handler is DefinedFunctionHandler && handler.function.flags.contains(FunctionFlag.DICT)) {
        throw ExException(
          "E725: Calling dict function without Dictionary: " +
            (scope?.toString() ?: "") + functionName.evaluate(editor, context, parent)
        )
      }
      return handler.executeFunction(this.arguments, editor, context, parent)
    }

    val funcref = VimPlugin.getVariableService().getNullableVariableValue(Variable(scope, functionName), editor, context, parent)
    if (funcref is VimFuncref) {
      val name = (if (scope != null) scope.c + ":" else "") + functionName
      return funcref.execute(name, arguments, editor, context, parent)
    }
    throw ExException("E117: Unknown function: ${if (scope != null) scope.c + ":" else ""}${functionName.evaluate(editor, context, parent)}")
  }
}
