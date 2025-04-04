package cc.unitmesh.devti.actions.chat

import cc.unitmesh.devti.actions.chat.base.ChatCheckForUpdateAction
import cc.unitmesh.devti.gui.chat.message.ChatActionType
import cc.unitmesh.devti.gui.sendToChatWindow
import cc.unitmesh.devti.intentions.action.ElementSelectionForChat
import cc.unitmesh.devti.settings.locale.LanguageChangedCallback.presentationText
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import cc.unitmesh.devti.intentions.action.getElementToAction

class ChatWithThisAction : ChatCheckForUpdateAction() {
    init{
        presentationText("settings.autodev.rightClick.chat", templatePresentation)
    }
    override fun getActionType(): ChatActionType = ChatActionType.CHAT

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return

        val caretModel = event.getData(CommonDataKeys.EDITOR)?.caretModel
        var prefixText = caretModel?.currentCaret?.selectedText ?: ""

        if (prefixText.isEmpty()) {
            val element = getElementToAction(project, editor)
            if (element != null) {
                ElementSelectionForChat.selectElement(element, editor)
                prefixText = element.text
            }
        }

        val language = event.getData(CommonDataKeys.PSI_FILE)?.language?.displayName ?: ""

        sendToChatWindow(project, getActionType()) { contentPanel, _ ->
            contentPanel.setInput("\n```$language\n$prefixText\n```")
        }
    }
}

