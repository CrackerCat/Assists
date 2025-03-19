package com.ven.assists

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.os.bundleOf
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.ven.assists.service.AssistsService
import com.ven.assists.utils.CoroutineWrapper
import com.ven.assists.utils.NodeClassValue
import com.ven.assists.utils.runMain
import com.ven.assists.window.AssistsWindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/**
 * 无障碍服务核心类
 * 提供对AccessibilityService的封装和扩展功能
 */
object AssistsCore {
    /** 日志标签 */
    var LOG_TAG = "assists_log"

    /** 当前应用在屏幕中的位置信息缓存 */
    private var appRectInScreen: Rect? = null

    /**
     * 以下是一系列用于快速判断元素类型的扩展函数
     * 通过比对元素的className来判断元素类型
     */
    
    /** 判断元素是否是FrameLayout */
    fun AccessibilityNodeInfo.isFrameLayout(): Boolean {
        return className == NodeClassValue.FrameLayout
    }

    /** 判断元素是否是ViewGroup */
    fun AccessibilityNodeInfo.isViewGroup(): Boolean {
        return className == NodeClassValue.ViewGroup
    }

    /** 判断元素是否是View */
    fun AccessibilityNodeInfo.isView(): Boolean {
        return className == NodeClassValue.View
    }

    /** 判断元素是否是ImageView */
    fun AccessibilityNodeInfo.isImageView(): Boolean {
        return className == NodeClassValue.ImageView
    }

    /** 判断元素是否是TextView */
    fun AccessibilityNodeInfo.isTextView(): Boolean {
        return className == NodeClassValue.TextView
    }

    /** 判断元素是否是LinearLayout */
    fun AccessibilityNodeInfo.isLinearLayout(): Boolean {
        return className == NodeClassValue.LinearLayout
    }

    /** 判断元素是否是RelativeLayout */
    fun AccessibilityNodeInfo.isRelativeLayout(): Boolean {
        return className == NodeClassValue.RelativeLayout
    }

    /** 判断元素是否是Button */
    fun AccessibilityNodeInfo.isButton(): Boolean {
        return className == NodeClassValue.Button
    }

    /** 判断元素是否是ImageButton */
    fun AccessibilityNodeInfo.isImageButton(): Boolean {
        return className == NodeClassValue.ImageButton
    }

    /** 判断元素是否是EditText */
    fun AccessibilityNodeInfo.isEditText(): Boolean {
        return className == NodeClassValue.EditText
    }

    /**
     * 获取元素的文本内容
     * @return 元素的text属性值，如果为空则返回空字符串
     */
    fun AccessibilityNodeInfo.txt(): String {
        return text?.toString() ?: ""
    }

    /**
     * 获取元素的描述内容
     * @return 元素的contentDescription属性值，如果为空则返回空字符串
     */
    fun AccessibilityNodeInfo.des(): String {
        return contentDescription?.toString() ?: ""
    }

    /**
     * 初始化AssistsCore
     * @param application Application实例
     */
    fun init(application: Application) {
        LogUtils.getConfig().globalTag = LOG_TAG
    }

    /**
     * 打开系统的无障碍服务设置页面
     * 用于引导用户开启无障碍服务
     */
    fun openAccessibilitySetting() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        ActivityUtils.startActivity(intent)
    }

    /**
     * 检查无障碍服务是否已开启
     * @return true表示服务已开启，false表示服务未开启
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return AssistsService.instance != null
    }

    /**
     * 获取当前窗口所属的应用包名
     * @return 当前窗口的包名，如果获取失败则返回空字符串
     */
    fun getPackageName(): String {
        return AssistsService.instance?.rootInActiveWindow?.packageName?.toString() ?: ""
    }

    /**
     * 通过id查找所有符合条件的元素
     * @param id 元素的资源id
     * @param text 可选的文本过滤条件
     * @return 符合条件的元素列表
     */
    fun findById(id: String, text: String? = null): List<AccessibilityNodeInfo> {
        var nodeInfos = AssistsService.instance?.rootInActiveWindow?.findById(id) ?: arrayListOf()

        nodeInfos = text?.let {
            nodeInfos.filter {
                if (it.txt() == text) {
                    return@filter true
                }
                return@filter false
            }
        } ?: let { nodeInfos }

        return nodeInfos
    }

    /**
     * 在指定元素范围内通过id查找所有符合条件的元素
     * @param id 元素的资源id
     * @return 符合条件的元素列表
     */
    fun AccessibilityNodeInfo?.findById(id: String): List<AccessibilityNodeInfo> {
        if (this == null) return arrayListOf()
        findAccessibilityNodeInfosByViewId(id)?.let {
            return it
        }
        return arrayListOf()
    }

    /**
     * 通过文本内容查找所有符合条件的元素
     * @param text 要查找的文本内容
     * @return 符合条件的元素列表
     */
    fun findByText(text: String): List<AccessibilityNodeInfo> {
        return AssistsService.instance?.rootInActiveWindow?.findByText(text) ?: arrayListOf()
    }

    /**
     * 查找所有文本完全匹配的元素
     * @param text 要匹配的文本内容
     * @return 文本完全匹配的元素列表
     */
    fun findByTextAllMatch(text: String): List<AccessibilityNodeInfo> {
        val listResult = arrayListOf<AccessibilityNodeInfo>()
        val list = AssistsService.instance?.rootInActiveWindow?.findByText(text)
        list?.let {
            it.forEach {
                if (TextUtils.equals(it.text, text)) {
                    listResult.add(it)
                }
            }
        }
        return listResult
    }

    /**
     * 在指定元素范围内通过文本查找所有符合条件的元素
     * @param text 要查找的文本内容
     * @return 符合条件的元素列表
     */
    fun AccessibilityNodeInfo?.findByText(text: String): List<AccessibilityNodeInfo> {
        if (this == null) return arrayListOf()
        findAccessibilityNodeInfosByText(text)?.let {
            return it
        }
        return arrayListOf()
    }

    /**
     * 判断元素是否包含指定文本
     * @param text 要检查的文本内容
     * @return true表示包含指定文本，false表示不包含
     */
    fun AccessibilityNodeInfo?.containsText(text: String): Boolean {
        if (this == null) return false
        getText()?.let {
            if (it.contains(text)) return true
        }
        contentDescription?.let {
            if (it.contains(text)) return true
        }
        return false
    }

    /**
     * 获取元素的所有文本内容（包括text和contentDescription）
     * @return 包含所有文本内容的列表
     */
    fun AccessibilityNodeInfo?.getAllText(): ArrayList<String> {
        if (this == null) return arrayListOf()
        val texts = arrayListOf<String>()
        getText()?.let {
            texts.add(it.toString())
        }
        contentDescription?.let {
            texts.add(it.toString())
        }
        return texts
    }

    /**
     * 根据多个条件查找元素
     * @param className 元素的类名
     * @param viewId 可选的资源id过滤条件
     * @param text 可选的文本过滤条件
     * @param des 可选的描述文本过滤条件
     * @return 符合所有条件的元素列表
     */
    fun findByTags(
        className: String,
        viewId: String? = null,
        text: String? = null,
        des: String? = null
    ): List<AccessibilityNodeInfo> {
        var nodeList = arrayListOf<AccessibilityNodeInfo>()
        getAllNodes().forEach {
            if (TextUtils.equals(className, it.className)) {
                nodeList.add(it)
            }
        }
        nodeList = viewId?.let {
            return@let arrayListOf<AccessibilityNodeInfo>().apply {
                addAll(nodeList.filter {
                    return@filter it.viewIdResourceName == viewId
                })
            }
        } ?: let {
            return@let nodeList
        }

        nodeList = text?.let {
            return@let arrayListOf<AccessibilityNodeInfo>().apply {
                addAll(nodeList.filter {
                    return@filter it.txt() == text
                })
            }
        } ?: let { return@let nodeList }
        nodeList = des?.let {
            return@let arrayListOf<AccessibilityNodeInfo>().apply {
                addAll(nodeList.filter {
                    return@filter it.des() == des
                })
            }
        } ?: let { return@let nodeList }
        return nodeList
    }

    /**
     * 在指定元素范围内根据多个条件查找元素
     * @param className 元素的类名
     * @param viewId 可选的资源id过滤条件
     * @param text 可选的文本过滤条件
     * @param des 可选的描述文本过滤条件
     * @return 符合所有条件的元素列表
     */
    fun AccessibilityNodeInfo.findByTags(
        className: String,
        viewId: String? = null,
        text: String? = null,
        des: String? = null
    ): List<AccessibilityNodeInfo> {
        var nodeList = arrayListOf<AccessibilityNodeInfo>()
        getNodes().forEach {
            if (TextUtils.equals(className, it.className)) {
                nodeList.add(it)
            }
        }
        nodeList = viewId?.let {
            return@let arrayListOf<AccessibilityNodeInfo>().apply {
                addAll(nodeList.filter {
                    return@filter it.viewIdResourceName == viewId
                })
            }
        } ?: let {
            return@let nodeList
        }

        nodeList = text?.let {
            return@let arrayListOf<AccessibilityNodeInfo>().apply {
                addAll(nodeList.filter {
                    return@filter it.txt() == text
                })
            }
        } ?: let { return@let nodeList }
        nodeList = des?.let {
            return@let arrayListOf<AccessibilityNodeInfo>().apply {
                addAll(nodeList.filter {
                    return@filter it.des() == des
                })
            }
        } ?: let { return@let nodeList }

        return nodeList
    }

    /**
     * 查找第一个符合指定类型的父元素
     * @param className 要查找的父元素类名
     * @return 找到的父元素，如果未找到则返回null
     */
    fun AccessibilityNodeInfo.findFirstParentByTags(className: String): AccessibilityNodeInfo? {
        val nodeList = arrayListOf<AccessibilityNodeInfo>()
        findFirstParentByTags(className, nodeList)
        return nodeList.firstOrNull()
    }

    /**
     * 递归查找符合指定类型的父元素
     * @param className 要查找的父元素类名
     * @param container 用于存储查找结果的列表
     */
    fun AccessibilityNodeInfo.findFirstParentByTags(className: String, container: ArrayList<AccessibilityNodeInfo>) {
        getParent()?.let {
            if (TextUtils.equals(className, it.className)) {
                container.add(it)
            } else {
                it.findFirstParentByTags(className, container)
            }
        }
    }

    /**
     * 获取当前窗口中的所有元素
     * @return 包含所有元素的列表
     */
    fun getAllNodes(): ArrayList<AccessibilityNodeInfo> {
        val nodeList = arrayListOf<AccessibilityNodeInfo>()
        AssistsService.instance?.rootInActiveWindow?.getNodes(nodeList)
        return nodeList
    }

    /**
     * 获取指定元素下的所有子元素
     * @return 包含所有子元素的列表
     */
    fun AccessibilityNodeInfo.getNodes(): ArrayList<AccessibilityNodeInfo> {
        val nodeList = arrayListOf<AccessibilityNodeInfo>()
        this.getNodes(nodeList)
        return nodeList
    }

    /**
     * 递归获取元素的所有子元素
     * @param nodeList 用于存储子元素的列表
     */
    private fun AccessibilityNodeInfo.getNodes(nodeList: ArrayList<AccessibilityNodeInfo>) {
        nodeList.add(this)
        if (nodeList.size > 10000) return // 防止无限递归
        for (index in 0 until this.childCount) {
            getChild(index)?.getNodes(nodeList)
        }
    }

    /**
     * 查找元素的第一个可点击的父元素
     * @return 找到的可点击父元素，如果未找到则返回null
     */
    fun AccessibilityNodeInfo.findFirstParentClickable(): AccessibilityNodeInfo? {
        arrayOfNulls<AccessibilityNodeInfo>(1).apply {
            findFirstParentClickable(this)
            return this[0]
        }
    }

    /**
     * 递归查找可点击的父元素
     * @param nodeInfo 用于存储查找结果的数组
     */
    private fun AccessibilityNodeInfo.findFirstParentClickable(nodeInfo: Array<AccessibilityNodeInfo?>) {
        if (parent?.isClickable == true) {
            nodeInfo[0] = parent
            return
        } else {
            parent?.findFirstParentClickable(nodeInfo)
        }
    }

    /**
     * 获取元素的直接子元素（不包括子元素的子元素）
     * @return 包含直接子元素的列表
     */
    fun AccessibilityNodeInfo.getChildren(): ArrayList<AccessibilityNodeInfo> {
        val nodes = arrayListOf<AccessibilityNodeInfo>()
        for (i in 0 until this.childCount) {
            val child = getChild(i)
            nodes.add(child)
        }
        return nodes
    }

    /**
     * 执行手势操作
     * @param gesture 手势描述对象
     * @param nonTouchableWindowDelay 窗口变为不可触摸后的延迟时间
     * @return 手势是否执行成功
     */
    suspend fun dispatchGesture(
        gesture: GestureDescription,
        nonTouchableWindowDelay: Long = 100,
    ): Boolean {
        val completableDeferred = CompletableDeferred<Boolean>()

        val gestureResultCallback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                CoroutineWrapper.launch { AssistsWindowManager.touchableByAll() }
                completableDeferred.complete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                CoroutineWrapper.launch { AssistsWindowManager.touchableByAll() }
                completableDeferred.complete(false)
            }
        }
        val runResult = AssistsService.instance?.let {
            AssistsWindowManager.nonTouchableByAll()
            delay(nonTouchableWindowDelay)
            runMain { it.dispatchGesture(gesture, gestureResultCallback, null) }
        } ?: let {
            return false
        }
        if (!runResult) return false
        return completableDeferred.await()
    }

    /**
     * 执行点击或滑动手势
     * @param startLocation 起始位置坐标
     * @param endLocation 结束位置坐标
     * @param startTime 开始延迟时间
     * @param duration 手势持续时间
     * @return 手势是否执行成功
     */
    suspend fun gesture(
        startLocation: FloatArray,
        endLocation: FloatArray,
        startTime: Long,
        duration: Long,
    ): Boolean {
        val path = Path()
        path.moveTo(startLocation[0], startLocation[1])
        path.lineTo(endLocation[0], endLocation[1])
        return gesture(path, startTime, duration)
    }

    /**
     * 执行自定义路径的手势
     * @param path 手势路径
     * @param startTime 开始延迟时间
     * @param duration 手势持续时间
     * @return 手势是否执行成功
     */
    suspend fun gesture(
        path: Path,
        startTime: Long,
        duration: Long,
    ): Boolean {
        val builder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, startTime, duration)
        val gestureDescription = builder.addStroke(strokeDescription).build()
        val deferred = CompletableDeferred<Boolean>()
        val runResult = runMain {
            return@runMain AssistsService.instance?.dispatchGesture(gestureDescription, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    deferred.complete(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    deferred.complete(false)
                }
            }, null) ?: let {
                return@runMain false
            }
        }
        if (!runResult) return false
        val result = deferred.await()
        return result
    }

    /**
     * 获取元素在屏幕中的位置信息
     * @return 包含元素位置信息的Rect对象
     */
    fun AccessibilityNodeInfo.getBoundsInScreen(): Rect {
        val boundsInScreen = Rect()
        getBoundsInScreen(boundsInScreen)
        return boundsInScreen
    }

    /**
     * 点击元素
     * @return 点击操作是否成功
     */
    fun AccessibilityNodeInfo.click(): Boolean {
        return performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * 长按元素
     * @return 长按操作是否成功
     */
    fun AccessibilityNodeInfo.longClick(): Boolean {
        return performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    /**
     * 在指定坐标位置执行点击手势
     * @param x 横坐标
     * @param y 纵坐标
     * @param duration 点击持续时间
     * @return 手势是否执行成功
     */
    suspend fun gestureClick(
        x: Float,
        y: Float,
        duration: Long = 10
    ): Boolean {
        return gesture(
            floatArrayOf(x, y), floatArrayOf(x, y),
            0,
            duration,
        )
    }

    /**
     * 在元素位置执行点击手势
     * @param offsetX X轴偏移量
     * @param offsetY Y轴偏移量
     * @param switchWindowIntervalDelay 窗口切换延迟时间
     * @param duration 点击持续时间
     * @return 手势是否执行成功
     */
    suspend fun AccessibilityNodeInfo.nodeGestureClick(
        offsetX: Float = ScreenUtils.getScreenWidth() * 0.01953f,
        offsetY: Float = ScreenUtils.getScreenWidth() * 0.01953f,
        switchWindowIntervalDelay: Long = 250,
        duration: Long = 25
    ): Boolean {
        runMain { AssistsWindowManager.nonTouchableByAll() }
        delay(switchWindowIntervalDelay)
        val rect = getBoundsInScreen()
        val result = gesture(
            floatArrayOf(rect.left.toFloat() + offsetX, rect.top.toFloat() + offsetY),
            floatArrayOf(rect.left.toFloat() + offsetX, rect.top.toFloat() + offsetY),
            0,
            duration,
        )
        delay(switchWindowIntervalDelay)
        runMain { AssistsWindowManager.touchableByAll() }
        return result
    }

    /**
     * 执行返回操作
     * @return 返回操作是否成功
     */
    fun back(): Boolean {
        return AssistsService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
    }

    /**
     * 返回主屏幕
     * @return 返回主屏幕操作是否成功
     */
    fun home(): Boolean {
        return AssistsService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
    }

    /**
     * 打开通知栏
     * @return 打开通知栏操作是否成功
     */
    fun notifications(): Boolean {
        return AssistsService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) ?: false
    }

    /**
     * 显示最近任务
     * @return 显示最近任务操作是否成功
     */
    fun recentApps(): Boolean {
        return AssistsService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
    }

    /**
     * 向元素粘贴文本
     * @param text 要粘贴的文本
     * @return 粘贴操作是否成功
     */
    fun AccessibilityNodeInfo.paste(text: String?): Boolean {
        performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        AssistsService.instance?.let {
            val clip = ClipData.newPlainText("${System.currentTimeMillis()}", text)
            val clipboardManager = (it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            clipboardManager.setPrimaryClip(clip)
            clipboardManager.primaryClip
            return performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        return false
    }

    /**
     * 选择元素中的文本
     * @param selectionStart 选择起始位置
     * @param selectionEnd 选择结束位置
     * @return 文本选择操作是否成功
     */
    fun AccessibilityNodeInfo.selectionText(selectionStart: Int, selectionEnd: Int): Boolean {
        val selectionArgs = Bundle()
        selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, selectionStart)
        selectionArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, selectionEnd)
        return performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
    }

    /**
     * 设置元素的文本内容
     * @param text 要设置的文本
     * @return 设置文本操作是否成功
     */
    fun AccessibilityNodeInfo.setNodeText(text: String?): Boolean {
        text ?: return false
        return performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundleOf().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        })
    }

    /**
     * 根据基准宽度计算实际X坐标
     * @param baseWidth 基准宽度
     * @param x 原始X坐标
     * @return 计算后的实际X坐标
     */
    fun getX(baseWidth: Int, x: Int): Int {
        val screenWidth = ScreenUtils.getScreenWidth()
        return (x / baseWidth.toFloat() * screenWidth).toInt()
    }

    /**
     * 根据基准高度计算实际Y坐标
     * @param baseHeight 基准高度
     * @param y 原始Y坐标
     * @return 计算后的实际Y坐标
     */
    fun getY(baseHeight: Int, y: Int): Int {
        var screenHeight = ScreenUtils.getScreenHeight()
        if (screenHeight > baseHeight) {
            screenHeight = baseHeight
        }
        return (y.toFloat() / baseHeight * screenHeight).toInt()
    }

    /**
     * 获取当前应用在屏幕中的位置
     * @return 应用窗口的位置信息，如果未找到则返回null
     */
    fun getAppBoundsInScreen(): Rect? {
        return AssistsService.instance?.let {
            return@let findById("android:id/content").firstOrNull()?.getBoundsInScreen()
        }
    }

    /**
     * 初始化并缓存当前应用在屏幕中的位置
     * @return 应用窗口的位置信息
     */
    fun initAppBoundsInScreen(): Rect? {
        return getAppBoundsInScreen().apply {
            appRectInScreen = this
        }
    }

    /**
     * 获取当前应用在屏幕中的宽度
     * @return 应用窗口的宽度
     */
    fun getAppWidthInScreen(): Int {
        return appRectInScreen?.let {
            return@let it.right - it.left
        } ?: ScreenUtils.getScreenWidth()
    }

    /**
     * 获取当前应用在屏幕中的高度
     * @return 应用窗口的高度
     */
    fun getAppHeightInScreen(): Int {
        return appRectInScreen?.let {
            return@let it.bottom - it.top
        } ?: ScreenUtils.getScreenHeight()
    }

    /**
     * 向前滚动可滚动元素
     * @return 滚动操作是否成功
     */
    fun AccessibilityNodeInfo.scrollForward(): Boolean {
        return performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /**
     * 向后滚动可滚动元素
     * @return 滚动操作是否成功
     */
    fun AccessibilityNodeInfo.scrollBackward(): Boolean {
        return performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    /**
     * 在日志中输出元素的详细信息
     * @param tag 日志标签
     */
    fun AccessibilityNodeInfo.logNode(tag: String = LOG_TAG) {
        StringBuilder().apply {
            val rect = getBoundsInScreen()
            append("-------------------------------------\n")
            append("位置:left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom} \n")
            append("文本:$text \n")
            append("内容描述:$contentDescription \n")
            append("id:$viewIdResourceName \n")
            append("类型:${className} \n")
            append("是否已经获取到到焦点:$isFocused \n")
            append("是否可滚动:$isScrollable \n")
            append("是否可点击:$isClickable \n")
            append("是否可用:$isEnabled \n")
            Log.d(tag, toString())
        }
    }
}