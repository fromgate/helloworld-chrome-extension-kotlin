package helloworld

import chrome.storage.getSync
import chrome.tabs.ExecuteScriptDetails
import chrome.tabs.QueryInfo
import chrome.tabs.querySync
import org.w3c.dom.HTMLSelectElement
import kotlin.browser.document

/**
 * Get the current URL.
 *
 * @param callback called when the URL of the current tab
 *   is found.
 */
fun getCurrentTabUrlAsync() =
        async {
            // Query filter to be passed to chrome.tabs.query - see
            // https://developer.chrome.com/extensions/tabs#method-query
            val queryInfo = QueryInfo {
                active = true
                currentWindow = true
            }
            val tabs = querySync(queryInfo)
            // chrome.tabs.query invokes the callback with a list of tabs that match the
            // query. When the popup is opened, there is certainly a window and at least
            // one tab, so we can safely assume that |tabs| is a non-empty array.
            // A window can only have one active tab at a time, so the array consists of
            // exactly one tab.
            val tab = tabs[0]

            // A tab is a plain object that provides information about the tab.
            // See https://developer.chrome.com/extensions/tabs#type-Tab
            val url = tab.url
            // tab.url is only available if the "activeTab" permission is declared.
            // If you want to see the URL of other tabs (e.g. after removing active:true
            // from |queryInfo|), then the "tabs" permission is required to see their
            // "url" properties.
            url ?: throw RuntimeException("tab.url should be a string")
        }

/**
 * Change the background color of the current page.
 *
 * @param color The new background color.
 */
fun changeBackgroundColor(color: String) {
    val script = """document.body.style.backgroundColor="$color";"""
    // See https://developer.chrome.com/extensions/tabs#method-executeScript.
    // chrome.tabs.executeScript allows us to programmatically inject JavaScript
    // into a page. Since we omit the optional first argument "tabId", the script
    // is inserted into the active tab of the current window, which serves as the
    // default.
    chrome.tabs.executeScript(
            details = ExecuteScriptDetails {
                code = script
            }
    )
}

/**
 * Gets the saved background color for url.
 *
 * @param url URL whose background color is to be retrieved.
 * @param callback called with the saved background color for
 *     the given url on success, or a falsy value if no color is retrieved.
 */
fun getSavedBackgroundColorAsync(url: String) =
        async {
            // See https://developer.chrome.com/apps/storage#type-StorageArea. We check
            // for chrome.runtime.lastError to ensure correctness even when the API call
            // fails.
            val items = chrome.storage.sync.getSync(url)
            items[url] as? String
        }

/**
 * Sets the given background color for url.
 *
 * @param url URL for which background color is to be saved.
 * @param color The background color to be saved.
 */
fun saveBackgroundColor(url: String, color: String) {
    val items = js("{}")
    items[url] = color
    // See https://developer.chrome.com/apps/storage#type-StorageArea. We omit the
    // optional callback since we don't need to perform any action once the
    // background color is saved.
    chrome.storage.sync.set(items)
}

// This extension loads the saved background color for the current tab if one
// exists. The user can select a new background color from the dropdown for the
// current page, and it will be saved as part of the extension's isolated
// storage. The chrome.storage API is used for this purpose. This is different
// from the window.localStorage API, which is synchronous and stores data bound
// to a document's origin. Also, using chrome.storage.sync instead of
// chrome.storage.local allows the extension data to be synced across multiple
// user devices.
fun main(args: Array<String>) {
    document.onContentLoadedEventAsync {
        val url = getCurrentTabUrlAsync().await()
        val dropdown = document.getElementById("dropdown") as HTMLSelectElement
        // Load the saved background color for this page and modify the dropdown
        // value, if needed.
        val savedColor = getSavedBackgroundColorAsync(url).await()
        if (savedColor != null) {
            changeBackgroundColor(savedColor)
            dropdown.value = savedColor
        }
        // Ensure the background color is changed and saved when the dropdown
        // selection changes.
        dropdown.onChangeEvent {
            changeBackgroundColor(dropdown.value)
            saveBackgroundColor(url, dropdown.value)
        }
    }
}
