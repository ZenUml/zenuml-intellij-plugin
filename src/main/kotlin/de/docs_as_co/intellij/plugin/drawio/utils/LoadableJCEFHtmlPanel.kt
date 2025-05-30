package de.docs_as_co.intellij.plugin.drawio.utils

import com.intellij.CommonBundle
import com.intellij.ide.plugins.MultiPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.Alarm
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.network.CefRequest
import java.awt.BorderLayout
import javax.swing.JComponent

class LoadableJCEFHtmlPanel(
    url: String? = null, html: String? = null,
    var timeoutCallback: String? = EditorBundle.message("message.html.editor.timeout")
) : Disposable {
    private val htmlPanelComponent = JCEFHtmlPanel(
        JBCefApp.isOffScreenRenderingModeEnabled(),
        null,
        null)

    private val loadingPanel = JBLoadingPanel(BorderLayout(), this).apply { setLoadingText(CommonBundle.getLoadingTreeNodeText()) }
    private val alarm = Alarm()

    val browser: JBCefBrowserBase get() = htmlPanelComponent

    companion object {
        private const val LOADING_KEY = 1
        private const val CONTENT_KEY = 0
    }

    private val multiPanel: MultiPanel = object : MultiPanel() {
        override fun create(key: Int) = when (key) {
            LOADING_KEY -> loadingPanel
            CONTENT_KEY -> htmlPanelComponent.component
            else -> throw UnsupportedOperationException("Unknown key")
        }
    }

    init {
        if (url != null) {
            htmlPanelComponent.loadURL(url)
        }
        if (html != null) {
            htmlPanelComponent.loadHTML(html)
        }
        multiPanel.select(CONTENT_KEY, true)
    }

    init {
        // TODO: Remove this when https://github.com/docToolchain/diagrams.net-intellij-plugin/issues/343 is fixed
        // Disable out-of-process mode for JCEF
        Registry.get("ide.browser.jcef.out-of-process.enabled").setValue(false)
    }

    init {
        htmlPanelComponent.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadStart(browser: CefBrowser?, frame: CefFrame?, transitionType: CefRequest.TransitionType?) {
                alarm.addRequest({ htmlPanelComponent.setHtml(timeoutCallback!!) }, Registry.intValue("html.editor.timeout", 10000))
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                alarm.cancelAllRequests()
            }

            override fun onLoadingStateChange(browser: CefBrowser?, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
                if (isLoading) {
                    invokeLater {
                        loadingPanel.startLoading()
                        multiPanel.select(LOADING_KEY, true)
                    }
                } else {
                    invokeLater {
                        loadingPanel.stopLoading()
                        multiPanel.select(CONTENT_KEY, true)
                    }
                }
            }
        }, htmlPanelComponent.cefBrowser)
    }

    override fun dispose() {
        alarm.dispose()
    }

    val component: JComponent get() = this.multiPanel

}
