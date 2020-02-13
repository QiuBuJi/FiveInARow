package com.example.five_in_a_row

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Point
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.StringBuilder
import com.example.five_in_a_row.Circle.TYPE
import kotlin.math.abs

const val TAG = "信息"
lateinit var sharedPreferences: SharedPreferences
lateinit var edit: SharedPreferences.Editor
var isOpenAI = true

class MainActivity : AppCompatActivity() {
    private var adapter: Adapter? = null

    //region Companion
    companion object {
        lateinit var mTvTitle: TextView
        lateinit var mTvCircle: TextView
        val sizeScreen = Point()
        var heightMain: Int = 0
        var typeDefault: TYPE = TYPE.WHITE
    }
    //endregion

    //region onCreate
    @SuppressLint("CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPreferences = getSharedPreferences("FiveInARow", 0)
        edit = sharedPreferences.edit()

        //提取控件到全局变量
        mTvTitle = main_tvTitle
        mTvCircle = main_tvCircle

        heightMain = main_clMain.height
        windowManager.defaultDisplay.getSize(sizeScreen)//取屏幕尺寸

        //init others

        main_btClear.setOnClickListener { swapAdapter(point) }//监听器，清空棋盘
        main_btClear.performClick()//初始化棋盘

        main_btOpen.setOnClickListener {
            isOpenAI = if (isOpenAI) {
                main_btOpen.text = "开启AI"
                false
            } else {
                main_btOpen.text = "关闭AI"
                true
            }
        }
    }

    /**交换adapter*/
    private fun swapAdapter(point: Point) {
        adapter?.run {
            if (threadsSnake.isNotEmpty()) {
                for (thread in threadsSnake) thread.interrupt()
                threadsSnake.clear()
            }
        }
        //设置棋盘适配器
        adapter = Adapter(this, main_rvTable, point.x, point.y)
        main_rvTable.swapAdapter(adapter, true)
        main_rvTable.layoutManager = GridLayoutManager(this, adapter!!.width)
        main_rvTable.setHasFixedSize(true)
    }

    private val group1 = 1
    private val group2 = 2
    private val group3 = 3
    private val group4 = 4
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.run {
            addSubMenu(group1, 0, 0, "先下棋").run {
                add(group1, 1, 0, "白棋").setIcon(R.drawable.ic_bg_circle_white)
                add(group1, 2, 0, "黑棋").setIcon(R.drawable.ic_bg_circle_black)
            }

            addSubMenu(group2, 0, 0, "棋盘格数").run {
                add(group2, 1, 0, "6*6").setIcon(R.drawable.ic_bg_table)
                add(group2, 2, 0, "6*10").setIcon(R.drawable.ic_bg_table)
                add(group2, 3, 0, "10*10").setIcon(R.drawable.ic_bg_table)
                add(group2, 4, 0, "10*16").setIcon(R.drawable.ic_bg_table)
                add(group2, 5, 0, "16*16").setIcon(R.drawable.ic_bg_table)
                add(group2, 6, 0, "16*26").setIcon(R.drawable.ic_bg_table)
            }

            addSubMenu(group4, 0, 0, "存储").run {
                var msg = ""
                add(group4, 1, 0, "保存")
                val string = sharedPreferences.getString("table", "")
                if (string.isNotEmpty()) msg = " (有数据)"
                add(group4, 2, 0, "恢复$msg")
            }

            add(group3, 4, 0, "关于")
        }
        return super.onCreateOptionsMenu(menu)
    }

    private var point: Point = Point(7, 9)//初始棋盘格数
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        item?.run {
            when (groupId) {
                //设置哪种类型的棋先下
                group1 -> {
                    when (itemId) {
                        1 -> adapter!!.currentType = TYPE.WHITE
                        2 -> adapter!!.currentType = TYPE.BLACK
                    }
                }
                //设置棋盘格数
                group2 -> {
                    val split = title.split("*")
                    point = when (itemId) {
                        0 -> return@run
                        else -> Point(split[0].toInt(), split[1].toInt())
                    }
                    swapAdapter(point)
                }
                //跳转关于窗口
                group3 -> {
                    val intent = Intent(this@MainActivity, AboutActivity::class.java)
                    startActivity(intent)
                }
                //存储
                group4 -> {
                    when (itemId) {
                        //保存
                        1 -> {
                            adapter?.run {
                                val sb = StringBuilder()
                                for (circle in table) {
                                    if (circle.type != TYPE.NONE) sb.append("${circle.index}-${circle.type};")
                                }
                                edit.putString("table", sb.toString()).commit()
                                Toast.makeText(
                                    this@MainActivity,
                                    "保存成功！ 大小：${sb.length}",
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                            }
                        }
                        //恢复
                        2 -> {
                            val string = sharedPreferences.getString("table", "")
                            adapter?.run { for (circle in table) circle.clear() }

                            if (string.isNotEmpty()) {
                                val split = string.split(";")
                                for (str in split) {
                                    if (str.isEmpty()) continue

                                    val split1 = str.split("-")
                                    val index = split1[0].toInt()
                                    adapter?.run {
                                        table[index].run {
                                            type = TYPE.valueOf(split1[1])
                                            show = true
                                        }
                                    }
                                }
                                adapter?.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }
    //endregion

    //*************************************** Adapter **********************************************
    class Adapter(
        private val context: Context,
        private val rvTable: RecyclerView,
        val width: Int,
        val height: Int
    ) : RecyclerView.Adapter<Holder>() {
        val table = ArrayList<Circle>()

        init {
            val size = width * height
            for (index in 0 until size) table.add(Circle().apply { this.index = index })
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val from = LayoutInflater.from(context)
                .inflate(R.layout.sample_item_circle, parent, false)
            onceSetHeight()

            currentType = typeDefault//设置默认类型
            return Holder(from)
        }

        private var once: Boolean = false
        /**设置棋盘高度*/
        private fun onceSetHeight() {
            //只让它执行1次
            if (once) return
            once = true
            val tableHeight = rvTable.width / width * height
            val remain = heightMain - rvTable.y
            // FIXME: 2020.2.5 dynamic set the table height
//            if (size < remain) rvTable.layoutParams.height = size
        }

        override fun getItemCount(): Int = table.size

        //region Game Method
        //*********************************** Game Method ******************************************
        /**通知数据改变*/
        private val handler = Handler { it ->
            it.obj?.let { (it as () -> Unit).invoke() }
            true
        }

        //简化handler发送过程
        private fun Handler.send(action: () -> Unit) {
            val msg = Message()
            msg.obj = action
            handler.sendMessage(msg)
        }

        /**通知数据改变了*/
        private fun Circle.notifyChange() {
            handler.send { notifyItemChanged(index) }
        }

        /**遍历包围该位置的所有数据*/
        private fun Circle.traverseSurrounds(action: (circleD: CircleD) -> Boolean) {
            for (times in -1..1) {
                val y = index + (times * width)
                val cx = y / width
                if (y < 0) continue//上越界

                for (x in -1..1) {
                    val index = y + x
                    val cxTemp = index / width

                    //规避越界错误
                    if ((cx != cxTemp) ||//左右越界
                        (times == 0 && x == 0) ||//中间
                        (index < 0) || (index >= table.size)//上下越界
                    ) continue

                    val circleD = CircleD(table[index], getDirection(x, times))
                    if (!action(circleD)) return
                }
            }
        }

        /**转换为方位数据*/
        private fun getDirection(x: Int, y: Int): Direction {
            return when (x) {
                -1 ->
                    when (y) {
                        -1 -> Direction.LeftTop
                        0 -> Direction.Left
                        else -> Direction.LeftBottom
                    }
                0 ->
                    when (y) {
                        -1 -> Direction.Top
                        0 -> Direction.None
                        else -> Direction.Bottom
                    }
                else ->
                    when (y) {
                        -1 -> Direction.RightTop
                        0 -> Direction.Right
                        else -> Direction.RightBottom
                    }
            }
        }

        /**取包围该位置的所有数据*/
        private fun Circle.getSurrounds(action: (CircleD) -> Boolean = { true }): ArrayList<CircleD> {
            val surrounds = ArrayList<CircleD>()

            traverseSurrounds {
                if (action(it)) surrounds.add(it)
                true
            }
            return surrounds
        }

        /**往该方向前进*/
        private fun CircleD.traverseDirection(action: (circleD: CircleD) -> Boolean) {
            if (action(this)) {

                circle.traverseSurrounds {
                    if (it.direction == direction) {
                        it.traverseDirection(action)
                        false
                    } else true
                }
            }
        }

        private fun CircleD.getDirectionLine(action: (circleD: CircleD) -> Boolean = { it.circle.type != TYPE.NONE }): ArrayList<CircleD> {
            val line = ArrayList<CircleD>()
            //收集棋子为直线
            traverseDirection {
                if (action(it)) {
                    line.add(it)
                    true
                } else false
            }
            return line
        }

        /**取所有通过该点的直线*/
        private fun Circle.getLines(action: (circleD: CircleD) -> Boolean = { true }): ArrayLines {
            val lines = ArrayList<ArrayList<CircleD>>()

            //收集该点发散出去的所有直线
            traverseSurrounds {
                //收集棋子为直线
                val line = it.getDirectionLine(action)

                if (line.isNotEmpty()) lines.add(line)//不要空的
                true
            }

            return connectLines(lines)
        }

        /**连接两个线段*/
        private fun Circle.connectLines(
            lines: ArrayLines,
            mark: Boolean = false
        ): ArrayLines {
            val linesOut = ArrayLines()

            var dirS = 0
            fun markIt(line: ArrayList<CircleD>): ArrayList<CircleD> {
                return ArrayList<CircleD>().apply {
                    for (circleD in line) {
                        if (mark) {
                            dirS = circleD.circle.direS_int or circleD.direction.direS.value
                            circleD.circle.direS_int = dirS
                        }
                        add(circleD)
                    }
                }
            }

            //融合该直线与直线延长线上的直线
            while (lines.isNotEmpty()) {
                val lineConcat = ArrayList<CircleD>()
                val line = lines.removeAt(0)
                if (line.isEmpty()) continue//没数据，则下一轮
                val not = !line[0].direction//相反的直线类型

                line.reverse()//反向
                lineConcat.addAll(markIt(line))
                lineConcat.add(CircleD(this, line[0].direction))//添加自己
                direS_int = dirS or direS_int

                for (lineTemp in lines) {
                    if (lineTemp[0].direction == not) {
                        lineConcat.addAll(markIt(lineTemp))//添加剩下的
                        lines.remove(lineTemp)
                        break
                    }
                }
                if (line.isEmpty()) this.direS_int = dirS
                linesOut.add(lineConcat)
            }
            return linesOut
        }

        /**检测赢的一方*/
        private fun Circle.detectWon(): ArrayList<ArrayList<CircleD>>? {
            val linesOut = ArrayList<ArrayList<CircleD>>()
            val lines = getLines { it.circle.type == this.type }

            //大于等于5颗棋子在一条直线上，判断为赢
            for (line in lines) if (line.size >= 5) linesOut.add(line)
            return if (linesOut.isNotEmpty()) linesOut else null
        }

        /**获取所有大于1的五子棋直线*/
        private fun getTableLines(): ArrayList<ArrayList<CircleD>> {
            val lines = ArrayList<ArrayList<CircleD>>()

            //遍历整个棋盘
            for (currCircle in table) {
                if (currCircle.type == TYPE.NONE) continue//避开空格子

                val linesTemp = ArrayList<ArrayList<CircleD>>()
                //取currCircle周围棋子为currCircle.type类型的数据
                val surrounds = currCircle.getSurrounds { it.circle.type == currCircle.type }

                for (circleD in surrounds) {
                    //数据被用则不再使用
                    val state = circleD.direction.direS.value and circleD.circle.direS_int
                    if (state > 0) continue

                    //取周围该点的直线数据
                    val line = circleD.getDirectionLine { it.circle.type == currCircle.type }
                    if (line.isNotEmpty()) linesTemp.add(line)//不要空的
                }
                //连接直线
                val connectLines = currCircle.connectLines(linesTemp, true)
                lines.addAll(connectLines)

                //该点棋子没能组成直线，也要加入它
                if (surrounds.isEmpty()) {
                    val arrayList = ArrayList<CircleD>()
                    arrayList.add(CircleD(currCircle, Direction.None))
                    lines.add(arrayList)
                }
            }
            for (circle in table) circle.direS_int = 0

            //从小到大排序
            for (line in lines) line.sortBy { it.circle.index }

            return lines
        }

        //endregion
        //******************************************************************************************
        /**当前该下棋的类型*/  //这个初始化不管用
        var currentType: TYPE = typeDefault
            @SuppressLint("SetTextI18n")
            set(value) {
                field = value
                mTvTitle.text = "${value.name1}下棋："
                mTvCircle.setBackgroundResource(value.resource)
            }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.run {
                /**棋子框的边长*/
                val sizePieces = rvTable.width / width
                val sizeReal = sizePieces * 0.4f
                val circle = table[position]

                itemView.layoutParams.height = sizePieces//配置初始化数据
                circle.tvText = tvCircle
                circle.tvText2 = tvText2
                tvText2.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeReal)

                tvCircle.run {
                    //文字尺寸适配框大小
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeReal)
                    layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

                    text = circle.text
                    setTextColor(circle.textColor)
                    setBackgroundResource(circle.type.resource)
                    setOnClickListener { circle.clicked(isOpenAI) }
                    setOnLongClickListener {
                        currentType = circle.type
                        circle.type = TYPE.NONE
                        circle.show = false
                        circle.notifyChange()
                        true
                    }
                }

                //显示下标
                tvText3.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePieces * 0.16f)
                tvText3.text = ""
//                tvText3.text = position.toString()
            }
        }

        private var mIsWon: Boolean = false
        /**格子被单击*/
        private fun Circle.clicked(runAI: Boolean = true) {
            val circle = this

            //设置不能在同一个地方重复下棋
            if (circle.show) {
//                showTableLines(circle.type)
//                circle.getLines()
                val tableLines = getTableLines()
                for (tableLine in tableLines) {
                    for (circleD in tableLine) {
                        if (circleD.circle == this) {
                            Thread {
                                val liveLines = tableLine.getSurroundLiveLines()
                                for (liveLine in liveLines) liveLine.flash(1000)
                            }.start()
                            return
                        }
                    }
                }
                return
            }
            //保存上一次，下的是什么棋

            circle.show = true
            circle.type = currentType
            currentType = currentType.alter
            typeDefault = currentType

            //检查棋盘
            val isWon = tableChecking(circle)
            if (isWon) mIsWon = true
            //通知该位置刷新显示的数据
            circle.notifyChange()

            if (runAI && !mIsWon) runAI(TYPE.BLACK)
        }

        /**获取两个棋子的方向*/
        private fun getBothDirS(circle1: Circle, circle2: Circle): DirectionS {
            val p1 = Point(circle1.index % width, circle1.index / width)
            val p2 = Point(circle2.index % width, circle2.index / width)

            val x = abs(p1.x - p2.x)
            val y = abs(p1.y - p2.y)

            return when {
                p1.y == p2.y -> DirectionS.Horizontal
                p1.x == p2.x -> DirectionS.Vertical
                x == y -> {
                    when {
                        p1.x < p2.x && p1.y < p2.y || p1.x > p2.x && p1.y > p2.y -> DirectionS.CantLeft
                        p1.x < p2.x && p1.y > p2.y || p1.x > p2.x && p1.y < p2.y -> DirectionS.CantRight
                        else -> DirectionS.None
                    }
                }
                else -> DirectionS.None
            }
        }

        private fun showTableLines(type: TYPE) {
            val tableLines =
                getTableLines().filter { it[0].circle.type == type } as ArrayList<ArrayList<CircleD>>
            tableLines.sortByDescending { it.size }
            for (tableLine in tableLines) tableLine.flash()
        }

        private fun ArrayLine.flash(duration: Long = 600) {
            val duration = duration / 2

            if (this.isNotEmpty()) {
                Thread {
                    for (circleD in this) {
                        handler.send {
                            circleD.circle.tvText2?.text = "●"
                            circleD.circle.tvText2?.setTextColor(circleD.circle.type.alter.color)
                        }
                    }

                    Thread.sleep(duration)
                    for (circleD in this) {
                        handler.send {
                            circleD.circle.tvText2?.text = ""
                            circleD.circle.tvText2?.setTextColor(circleD.circle.type.color)
                        }
                    }
                    Thread.sleep(duration)

                }.start()
            }
        }

        /**电脑下棋AI*/
        private fun runAI(type: TYPE) {
            if (currentType != type) return

            val thread = Thread {
                Thread.sleep(500)

                logBuffer.append("------------------------------------------------------------------------\n")

                val group = getTableLines().groupBy { it.first().circle.type }

                //排除不属于自己的类型
                val linesMyOwn = (group[type] ?: ArrayLines()) as ArrayLines
                val linesEnemy = (group[type.alter] ?: ArrayLines()) as ArrayLines
                linesMyOwn.sortByDescending { it.size }//大到小排序
                linesEnemy.sortByDescending { it.size }//大到小排序

                var enemyThreat: ArrayLine? = null
                var myOwnThreatLine: ArrayLine? = null

                //去除无意义直线
                val deadLinesMyOwn = linesMyOwn.removeDeadLines()
                val deadLinesEnemy = linesEnemy.removeDeadLines()

                try {
                    enemyThreat = linesEnemy.first()
                    myOwnThreatLine = linesMyOwn.first()
                } catch (e: Exception) {
                }

                //直线两端的空格子
                var lineEndBlank = ArrayLine()
                val outLine = ArrayLine()
                val myOwn1 = conditionInternalBlank(linesMyOwn)
                val enemy1 = conditionInternalBlank(linesEnemy)
                val myOwn2 = conditionXCenter(linesMyOwn)
                val enemy2 = conditionXCenter(linesEnemy)

                if (myOwn1.isNotEmpty()) {
                    outLine.addAll(myOwn1)
                    logd("我方有中间点")
                }
                if (enemy1.isNotEmpty()) {
                    if (myOwn1.isEmpty()) outLine.addAll(enemy1)
                    logd("敌方有中间点")
                }
                if (myOwn2.isNotEmpty()) {
                    outLine.addAll(myOwn2)
                    logd("我方有交叉数据")
                }
                if (enemy2.isNotEmpty()) {
                    if (myOwn1.isEmpty()) outLine.addAll(enemy2)
                    logd("敌方有交叉数据")
                }

                val myOwnSize = myOwnThreatLine?.size ?: 0
                val enemySize = enemyThreat?.size ?: 0

                //AI逻辑代码部分---------------------------------------------------------------------
                when {
                    outLine.isNotEmpty() && myOwnSize < 4 && enemySize < 4 -> {
                        lineEndBlank.addAll(outLine)
                        logd("特殊情况： $outLine")
                    }
                    //紧急情况处理 ******************************************************************
                    enemyThreat != null && (enemyThreat.size >= 3 || linesMyOwn.isEmpty()) -> {

                        //enemyThreat直线长度大于等于3就满足条件，如果它是半阻塞状态则不能满足
                        val stateOpen = enemyThreat.blockedNumber() == 0//是否为半开闭区间
                        val stateLength = enemyThreat.size >= 3 && stateOpen//条件：直线长度大于2，且为开放状态
                        //enemyThreat直线长度>=4,已经迫在眉睫，敌人快赢了！
                        val stateLengthThreat = enemyThreat.size >= 4
                        val empty = deadLinesMyOwn.isEmpty()


                        logd("敌人长度: ${enemyThreat.size}  处于${if (stateOpen) "开" else "半开"}放状态")
                        when {
                            //敌人棋子长度是3了，不得不扼杀它 ****************************************
                            stateLength || stateLengthThreat || empty -> {

                                //myOwnThreat自己的直线，如果长度>=3占优势，满足条件，给自己优势的直线继续扩展
                                var isIntrude = myOwnSize >= 3 && enemyThreat.size <= 3//进攻的条件

                                if ((enemyThreat.size == 3 && !stateOpen)) isIntrude = false
                                if (myOwnSize >= 4) isIntrude = true

                                if (myOwnThreatLine != null && isIntrude) {//增强自己
                                    logd("增强自己")
                                    val linePort = myOwnThreatLine.getLineEndsBlank()
                                    val lineAllBlank = myOwnThreatLine.getLineAllBlanks()
                                    val first = lineAllBlank.first()
                                    val last = lineAllBlank.last()

                                    linePort.removeIf1 { it == first || it == last }

                                    if (linePort.isNotEmpty()) {
                                        logd("选择最佳的一段 $linePort")
                                        lineEndBlank = linePort
                                    } else {
                                        lineEndBlank = lineAllBlank
                                        lineEndBlank.removeIf1 { it.circle.type != TYPE.NONE }
                                        logd("直线选取 $lineEndBlank")
                                    }

                                } else { //压制敌人
                                    logd("压制敌人")
                                    lineEndBlank = enemyThreat.getLineEndsBlank()
                                }

                                logd("可选下棋点： $lineEndBlank")
                            }
                            //没有可发展的直线了*****************************************************
                            linesMyOwn.isEmpty() -> {
                                //从自己死胡同了的直线，发展分支
                                if (deadLinesMyOwn.isNotEmpty()) {
                                    logd("有封闭直线数据 ${deadLinesMyOwn.first()} ${deadLinesMyOwn.last()}")

                                    val iterator = deadLinesMyOwn.iterator()
                                    while (iterator.hasNext()) {
                                        val line = iterator.next()
//                                        val deadLine = deadLinesMyOwn.random()//随机取一条死直线

                                        val liveLines = line.getSurroundLiveLines()//取活直线
                                        liveLines.removeIf1 { it.size < 5 }//去除没有赢意图的直线
                                        liveLines.sortByDescending { it.size }//从大到小排序

                                        if (liveLines.isNotEmpty()) {
                                            val liveLine = liveLines.first()
                                            liveLine.removeIf1 { it.circle.type != TYPE.NONE }
                                            lineEndBlank = liveLine

                                            logd("得到开放直线")
                                            break
                                        } else {
                                            logd("未得到开放直线，继续循环...")

                                            iterator.remove()
                                        }
                                    }

                                    if (deadLinesMyOwn.isEmpty()) {
                                        val edgeBlanks = ArrayLine()
                                        lineEndBlank =
                                            enemyThreat.getLineAllBlanks(edgeBlanks)//压制敌人

                                        if (edgeBlanks.isNotEmpty()) {
                                            lineEndBlank = edgeBlanks

                                            logd("压制敌人，可选下棋点： $edgeBlanks")
                                        }

                                    }

                                } else {
                                    logd("无封闭直线数据")
                                }

                            }
                            //其它状况**************************************************************
                            else -> {
                                //随机，进攻还是防御
                                val isIntrude = arrayListOf(true, false).random()
                                lineEndBlank =
                                    if (isIntrude) myOwnThreatLine!!.getLineEndsBlank()//增强自己
                                    else enemyThreat.getLineEndsBlank()//压制敌人

                                lineEndBlank.removeIf { it.circle.type != TYPE.NONE }
                                logd("随机状态: ${if (isIntrude) "进攻" else "防御"}")
                            }
                        }

                    }
                    //分支 *************************************************************************
                    else -> {
                        logd("增强自己")

                        var hasData = false
                        for ((index, line) in linesMyOwn.withIndex()) {
                            val bothBlank = ArrayLine()

                            if (line.size == 1) {
                                val remainLines = linesMyOwn.drop(index + 1)//去前面数据
                                var bothDirs: DirectionS = DirectionS.None

                                for (remainLine in remainLines) {
                                    bothDirs = getBothDirS(line[0].circle, remainLine[0].circle)

                                    if (bothDirs != DirectionS.None) {
                                        bothBlank.add(line[0])
                                        bothBlank.add(remainLine[0])
                                        break
                                    }
                                }

                                //只有1点
                                if (linesMyOwn.size == 1) {
                                    if (conditionOnlyOneCircle(line, lineEndBlank)) {
                                        hasData = true
                                        break
                                    }
                                } else if (bothBlank.isNotEmpty()) {
                                    //扩展我方最有潜力的直线，直线越长权重越大
                                    //直线长度等于1时的分支***************************************************
                                    logd("2棋子: $bothBlank")

                                    val liveLines = line.getSurroundLiveLines()
                                    liveLines.removeIf1 { it[0].direction.direS != bothDirs }

                                    //闪烁*******
                                    for (line in liveLines) {
                                        val type = if (line.size + 1 < 5) TYPE.RED else TYPE.TEST
                                        val lineTemp = ArrayLine(line)

                                        lineTemp.removeIf1 { it.circle.type != TYPE.NONE }//去除非空棋子
                                        lineTemp.flashCircle(400, 1, type)
                                    }

                                    liveLines.removeIf1 { it.size < 5 }//不要最终长度不能达到5颗棋子的直线

                                    if (liveLines.isNotEmpty()) {
                                        val line = liveLines.random()

                                        var temp = line.dropWhile { it.circle.type == TYPE.NONE }
                                        temp = temp.dropLastWhile { it.circle.type == TYPE.NONE }
                                        temp = temp.filterNot { it.circle.type != TYPE.NONE }

                                        if (temp.isNotEmpty()) {
                                            line.clear()
                                            line.addAll(temp)
                                            logd("有中间数据 $temp")
                                        } else {
                                            line.removeIf1 { it.circle.type != TYPE.NONE }//去除非空棋子
                                            logd("有边缘数据 $line")
                                        }

                                        lineEndBlank.add(line.random())
                                        line.flashCircle(600, 3)
                                        hasData = true
                                        break
                                    } else {
                                        logd("liveLines 没有数据，继续循环")
                                    }

                                }
                            }
                            //直线长度>=2的分支************************************************
                            else {
                                if (conditionMore(line, lineEndBlank, linesMyOwn, enemyThreat)) {
                                    hasData = true
                                    break
                                }
                            }//else end
                        }//for end

                        if (!hasData) {
                            logd("2个点行不通")

                            for (line in linesMyOwn) {
                                val liveLines = line.getSurroundLiveLines()

                                //闪烁*******
                                for (line in liveLines) {
                                    val type = if (line.size + 1 < 5) TYPE.RED else TYPE.TEST
                                    val lineTemp = ArrayLine(line)

                                    lineTemp.removeIf1 { it.circle.type != TYPE.NONE }//去除非空棋子
                                    lineTemp.flashCircle(400, 1, type)
                                }

                                liveLines.removeIf1 { it.size < 5 }//不要最终长度不能达到5颗棋子的直线

                                if (liveLines.isNotEmpty()) {
                                    val line = liveLines.random()

                                    var temp = line.dropWhile { it.circle.type == TYPE.NONE }
                                    temp = temp.dropLastWhile { it.circle.type == TYPE.NONE }
                                    temp = temp.filterNot { it.circle.type != TYPE.NONE }

                                    if (temp.isNotEmpty()) {
                                        line.clear()
                                        line.addAll(temp)
                                        logd("有中间数据 $temp")
                                    } else {
                                        line.removeIf1 { it.circle.type != TYPE.NONE }//去除非空棋子
                                        logd("有边缘数据 $line")
                                    }

                                    lineEndBlank = line
                                    line.flashCircle(600, 3)

                                    break
                                } else {
                                    logd("liveLines 没有数据，继续循环")
                                }
                            }
                        }
                    }//else end
                }//when end


                //下棋程序
                if (lineEndBlank.isNotEmpty()) {
                    lineEndBlank.flashCircle(300, 2)//闪烁潜在可下棋的位置

                    val blank = lineEndBlank.random()
                    handler.send { blank.circle.clicked() }

                    logd("下棋 $blank")
                } else {
                    logd("没棋可下")
                }

                //AI逻辑代码部分 End ----------------------------------------------------------------
                logd(commit = true)

            }//thread end
            thread.start()
        }

        /**两双棋子相交于一点*/
        private fun conditionXCenter(line: ArrayLines): ArrayLine {
            val outLine = ArrayLine()
            var keep = true
            val filter = line.filter { it.size >= 2 }
            if (filter.isEmpty()) return outLine

            val type = line[0][0].circle.type
            val tempBlanks = ArrayLine()
            for (tempLine in filter) tempBlanks.addAll(tempLine.getLineEndsBlank())//获取所有直线两端的空白格子

            for (circleD in tempBlanks) {
                val surroundBlanks = circleD.circle.getSurrounds { it.circle.type == type }//要本类型数据
                val tempLine = ArrayLine()

                //二直线端点相交判断
                for (blank in surroundBlanks) {
                    val dirLine = blank.getDirectionLine { it.circle.type == type }
                    val blockedNum = dirLine.blockedNumber()//不要阻塞的直线
                    if (dirLine.size >= 2 && blockedNum == 0) tempLine.add(circleD)
                }
                if (tempLine.size >= 2) outLine.add(circleD)

                //T字型判断
                for ((index, blank1) in surroundBlanks.withIndex()) {
                    for (indexTemp in index + 1 until surroundBlanks.size) {
                        val blank2 = surroundBlanks[indexTemp]
                        if (blank1.direction.direS == blank2.direction.direS) {
                            val directionLine1 =
                                blank1.getDirectionLine { it.circle.type != type.alter }
                            val directionLine2 =
                                blank2.getDirectionLine { it.circle.type != type.alter }
                            val length = directionLine1.size + directionLine2.size
                            if (length >= 4) outLine.add(circleD)
                            keep = false
                            break
                        }
                    }
                    if (!keep) break
                }
                if (!keep) break
            }
            outLine.removeIf1 { it.circle.type != TYPE.NONE }
            return outLine
        }

        /**直线中间有空格子，下棋继续就会输，判断*/
        private fun conditionInternalBlank(lines: ArrayLines): ArrayLine {
            val tempLines = ArrayLines()
            val outLine = ArrayLine()
            for (line in lines) tempLines.add(line.getLineAllBlanks())

            for (line in tempLines) {
                val indexOfFirst = line.indexOfFirst { it.circle.type != TYPE.NONE }
                val indexOfLast = line.indexOfLast { it.circle.type != TYPE.NONE }

                val abs = abs(indexOfFirst - indexOfLast) + 1
                if (abs >= 4) {
                    for (index in indexOfFirst..indexOfLast) outLine.add(line[index])
                    outLine.removeIf1 { it.circle.type != TYPE.NONE }
                    if (outLine.isNotEmpty()) break
                }
            }
            return outLine
        }

        private fun conditionOnlyOneCircle(line: ArrayLine, lineEndBlank: ArrayLine): Boolean {
            //扩展我方最有潜力的直线，直线越长权重越大
            //直线长度等于1时的分支***************************************************

            logd("单独棋子: ${line[0]}")
            val liveLines = line.getSurroundLiveLines()

            //闪烁*******
            for (line in liveLines) {
                val type = if (line.size + 1 < 5) TYPE.RED else TYPE.TEST
                val lineTemp = ArrayLine(line)

                lineTemp.removeIf1 { it.circle.type != TYPE.NONE }//去除非空棋子
                lineTemp.flashCircle(400, 1, type)
            }

            liveLines.removeIf1 { it.size < 5 }//不要最终长度不能达到5颗棋子的直线

            if (liveLines.isNotEmpty()) {
                val lineRandom = liveLines.random()
                val pos = lineRandom.indexOfFirst { it.circle.type != TYPE.NONE }
                val tempLine = ArrayLine()

                for (indexTemp in -1..1 step 2) {
                    val index = pos + indexTemp
                    if (index >= 0 && index < lineRandom.size) {
                        tempLine.add(lineRandom[index])
                    }
                }

                lineEndBlank.addAll(tempLine)
                lineEndBlank.flashCircle(600, 3)
                logd("下棋点 $lineEndBlank")
                return true
            } else {
                logd("liveLines 没有数据")
            }
            return false
        }

        //直线长度>=2的分支************************************************
        private fun conditionMore(
            line: ArrayLine,
            lineEndBlank: ArrayLine,
            linesMyOwn: ArrayLines,
            enemyThreat: ArrayLine?
        ): Boolean {
            if (line.size >= 3) {
                lineEndBlank.addAll(line.getLineEndsBlank())
                return true
            }

            val middleBlank = conditionInternalBlank(linesMyOwn)

            if (middleBlank.isNotEmpty()) {
                lineEndBlank.addAll(middleBlank)
                return true
            }

            val qualityLines = ArrayLines()
            for (line in linesMyOwn) qualityLines.add(line.getLineAllBlanks())

            qualityLines.sortByDescending {
                var count = 0
                for (circleD in it) if (circleD.circle.type != TYPE.NONE) count++
                count
            }

            if (qualityLines.isNotEmpty()) {
                qualityLines[0].getReasonableBlank(lineEndBlank)
                logd("线段: $lineEndBlank")
            }
            //没有增强自己的直线了
            else {
                logd("没有增强自己的直线了")

                enemyThreat?.let {
                    val edgeBlanks = ArrayLine()
                    lineEndBlank.addAll(it.getLineAllBlanks(edgeBlanks))//压制敌人

                    if (edgeBlanks.isNotEmpty()) {
                        lineEndBlank.clear()
                        lineEndBlank.addAll(edgeBlanks)
                        logd("压制敌人，可选下棋点： $edgeBlanks")
                    }
                }
            }
            return true
        }

        /**获取合理的下棋地点*/
        private fun ArrayLine.getReasonableBlank(lineEndBlank: ArrayLine) {
            var strSide: String
            val temp1 = ArrayLine()
            val temp2 = ArrayLine()
            var indexFirst = this.indexOfFirst { it.circle.type != TYPE.NONE }
            var indexLast = this.indexOfLast { it.circle.type != TYPE.NONE }
            val midLine = ArrayLine(this.subList(indexFirst, indexLast))
            midLine.removeIf { it.circle.type != TYPE.NONE }//去掉非空格子

            if (midLine.isEmpty()) {
                indexFirst--
                for (index in indexFirst downTo 0) {//获取第一个数据
                    temp1.add(this[index])
                    if (temp1.size >= 2) break
                }
                indexLast++
                for (index in indexLast until this.size) {//获取第二个数据
                    temp2.add(this[index])
                    if (temp2.size >= 2) break
                }
                val outLine = when {
                    temp1.size <= 1 -> temp2.apply { strSide = "→" }//temp1遇到墙壁了，换temp2的数据
                    temp2.size <= 1 -> temp1.apply { strSide = "←" }//temp2遇到墙壁了，换temp1的数据
                    else -> temp1.apply { addAll(temp2);strSide = "㊣" }//都没有遇到墙壁，返回全部数据
                }
                lineEndBlank.addAll(outLine)
                logd("外侧的空白格子：$strSide $outLine")
            } else {
                lineEndBlank.addAll(midLine)
                logd("得到中间的一段：$midLine ")
            }
        }

        private fun <E> ArrayList<E>.removeIf1(action: (E) -> Boolean) {
            val iterator = iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (action(next)) iterator.remove()
            }
        }

        /**让直线内的数据闪烁的显示*/
        private fun ArrayLine.flashCircle(
            duration: Int = 1000, times: Int = 3, type: TYPE = TYPE.TEST
        ) {
            val edgeBlanks: ArrayLine = this
            val duration = duration / 2 / times
            for (index in 1..times) {
                edgeBlanks.flash(type, duration)
                edgeBlanks.flash(TYPE.NONE, duration)
            }
        }

        /**1个闪烁周期*/
        private fun ArrayLine.flash(type: TYPE, duration: Int = 1000) {

            for (blank in this) {
                blank.circle.type = type
                blank.circle.notifyChange()
            }
            Thread.sleep(duration.toLong())
        }

        /**自定义Log可从Log字符串中定位到源代码*/
        private val logBuffer = StringBuilder()
        private val prefix: String = "runAI: "
        private fun logd(msg: String = "", commit: Boolean = false): Unit {
            val stackTrace = Thread.currentThread().stackTrace
            val trace: StackTraceElement = stackTrace[4]

            if (msg.isNotEmpty()) logBuffer.append("位置.(${trace.fileName}:${trace.lineNumber}) $prefix$msg \n")
            if (commit) {
                Log.d(TAG, logBuffer.toString())
                logBuffer.clear()
            }
        }

        /**获取直线两端被阻塞的数量*/
        private fun ArrayLine.blockedNumber(): Int {
            if (isEmpty()) return 0
            val first = CircleD(first())
            var numBlocked = 0

            first.traverseDirection {
                when (it.circle.type) {
                    first.circle.type -> true
                    TYPE.NONE -> false
                    else -> {
                        numBlocked++
                        false
                    }
                }
            }

            first.direction = first.direction.not()
            first.traverseDirection {
                when (it.circle.type) {
                    first.circle.type -> true
                    TYPE.NONE -> false
                    else -> {
                        numBlocked++
                        false
                    }
                }
            }
            return numBlocked
        }

        /**获取该直线周围所有有用的直线*/
        private fun ArrayLine.getSurroundLiveLines(): ArrayLines {
            val linesOut = ArrayLines()

            for (circleD in this) {
                //取环绕该点的棋子
                val surrounds = circleD.circle.getSurrounds {
                    //条件：不能为敌方棋子，也不能是相同方向的棋子
                    it.circle.type != circleD.circle.type.alter
                }
                val lines = ArrayLines()

                //取发散的直线
                for (surround in surrounds) {
                    val directionLine = surround.getDirectionLine {
                        //条件：不能为敌方棋子，也不能是相同方向的棋子
                        it.circle.type != circleD.circle.type.alter
                    }
                    lines.add(directionLine)
                }
                val connectLines = circleD.circle.connectLines(lines)

                //下标从小到大排序
                for (liveLine in connectLines) liveLine.sortBy { it.circle.index }

                linesOut.addAll(connectLines)
            }//for end

            linesOut.sortByDescending { it.size }//给自己从大到小排序

            return linesOut
        }

        /**去除没有胜利意义的直线*/
        private fun ArrayLines.removeDeadLines(): ArrayLines {
            val iterator = iterator()
            val deadLines = ArrayLines()

            while (iterator.hasNext()) {
                val line = iterator.next()
                val remainBlanks = line.getLineAllBlanks()

                remainBlanks.removeIf1 { it.circle.type != TYPE.NONE }
                val finalSize = remainBlanks.size + line.size

                if (finalSize < 5) {
                    deadLines.add(line)
                    iterator.remove()
                }
            }
            return deadLines
        }

        /**返回两端的空白*/
        private fun ArrayLine.getLineEndsBlank(): ArrayLine {
            val outLine = ArrayLine()

            if (isNotEmpty()) {
                val first = first()

                if (size == 1) return first.circle.getSurrounds()

                first.traverseDirection {
                    when (it.circle.type) {
                        first.circle.type -> true
                        TYPE.NONE -> {
                            outLine.add(it)
                            false
                        }
                        else -> false
                    }
                }

                first.direction = first.direction.not()
                first.traverseDirection {
                    when (it.circle.type) {
                        first.circle.type -> true
                        TYPE.NONE -> {
                            outLine.add(it)
                            false
                        }
                        else -> false
                    }
                }

            }
            return outLine
        }

        /**获取该直线还能继续扩展的地方*/
        private fun ArrayLine.getLineAllBlanks(edgeBlanks: ArrayLine? = null): ArrayLine {
            val arrayList = if (size == 1) {
                //取周围的空白格子
                val surrounds = first().circle.getSurrounds { it.circle.type == TYPE.NONE }
                edgeBlanks?.addAll(surrounds)
                surrounds
            } else {
                val first = first()
                val blanks = ArrayList<CircleD>()

                //取该方向的空白格子
                blanks.addAll(first.getDirectionBlanks()
                                  .apply { if (isNotEmpty()) edgeBlanks?.add(first()) })

                if (blanks.isNotEmpty()) blanks.removeAt(0)
                //取反方向的空白格子
                first.direction = !first.direction
                blanks.addAll(first.getDirectionBlanks()
                                  .apply { if (isNotEmpty()) edgeBlanks?.add(first()) })
                blanks
            }

            edgeBlanks?.run { sortBy { it.circle.index } }
            arrayList.sortBy { it.circle.index }
            return arrayList
        }

        /**取单方向的空白格子*/
        private fun CircleD.getDirectionBlanks(endOne: CircleD? = null): ArrayLine {
            val blanks = ArrayLine()
            var isAdd = true

            this.traverseDirection { it ->
                when (it.circle.type) {
                    TYPE.NONE -> {
                        blanks.add(it)
                        isAdd = false
                        true
                    }
                    circle.type -> {
                        blanks.add(it)

                        //保留末尾棋子数据
                        if (isAdd) endOne?.let { it.copy(this) }
                        true
                    }
                    else -> false
                }
            }
            return blanks
        }

        /**给直线每个格子设置文字*/
        private fun insertText(line: ArrayLine, txt: String): Unit {
            val iterator = txt.iterator()

            for (circle in line) {
                circle.run {
                    val strNext =
                        if (iterator.hasNext()) iterator.nextChar().toString() else ""

                    circle.circle.text = strNext
                }
            }
        }

        /**棋盘检查,检查哪方胜利*/
        private fun tableChecking(pCircle: Circle): Boolean {
            val detectWon = pCircle.detectWon()

            detectWon?.let {
                Thread {
                    val strWin = " 你真棒•"
                    val getC = strWin.iterator()
                    food = pCircle.type.alter
                    val tempType = pCircle.type

                    //有赢的数据，则执行赢程序 *******************************************************
                    for (line in it) {
                        for (circle in line) {
                            circle.circle.run {
                                text = if (getC.hasNext()) getC.next().toString() else ""
                                textColor = circle.circle.type.color
                                type = TYPE.TEST
                                notifyChange()
                            }
                            Thread.sleep(200)
                        }
                    }

                    //加入贪吃蛇
                    gameSnake(it, tempType)
                }.start()
                return true
            }
            return false
        }

        private var food = TYPE.FOOD

        val threadsSnake = ArrayList<Thread>()
        //贪吃蛇游戏代码部分 ************************************************************
        private fun gameSnake(it: ArrayLines, type: TYPE) {
            for (lineSnake in it) {
                Thread {
                    var direction: Direction = Direction.Right

                    //贪吃蛇“结束”程序
                    fun deathProgress(line: ArrayLine): Boolean {
                        //显示死时候的样貌
                        for (circle in line) {
                            circle.circle.textColor = Color.RED
                            circle.circle.notifyChange()
                        }
                        insertText(line, "我死了********")

                        //捕获中断退出贪吃蛇程序
                        try {
                            Thread.sleep(2000)
                        } catch (e: Exception) {
                            return true
                        }

                        //复原数据
                        for (circle in line) {
                            circle.circle.clear()
                            circle.circle.notifyChange()
                        }
                        return false
                    }

                    //***贪吃蛇循环***
                    while (true) {
                        var head = lineSnake.first()
                        if (head.circle.type == TYPE.NONE) break
                        head.circle.type = type

                        //行走方向获取
                        var surroundBoxes = head.circle.getSurrounds {
                            it.circle.type == food || it.circle.type == TYPE.NONE &&
                                    //不让斜着走
                                    when (it.direction) {
                                        Direction.RightTop,
                                        Direction.RightBottom,
                                        Direction.LeftTop,
                                        Direction.LeftBottom
                                        -> false
                                        else -> true
                                    }
                        }
                        if (surroundBoxes.isEmpty())
                            surroundBoxes = head.circle.getSurrounds {
                                it.circle.type == TYPE.NONE || it.circle.type == food
                            }

                        var random = try {
                            surroundBoxes.random()//随机获取方向
                        } catch (e: Exception) {
                            //没有方向可以行走，死亡程序开始
                            if (deathProgress(lineSnake)) break
                            break
                        }
                        //没有方向可以行走，死亡程序开始
                        if (surroundBoxes.isEmpty()) {
                            if (deathProgress(lineSnake)) break
                            break
                        }


                        var hasDir = false
                        for (box in surroundBoxes) {
                            box.traverseDirection {
                                if (it.circle.type == food) {
                                    random = box
                                    hasDir = true
                                    false
                                } else it.circle.type == TYPE.NONE
                            }
                            if (hasDir) break
                        }


                        if (hasDir) {

                        } else {
                            //走直线
                            for (box in surroundBoxes) if (box.direction == direction) random = box
                        }

                        val hasFood = random.circle.type == food
                        random.circle.copy(head.circle)
                        random.circle.notifyChange()
                        direction = random.direction

                        var temp = head
                        head = random

                        //向head方向移动数据
                        for (index in 1 until lineSnake.size) {
                            val circle = lineSnake[index]

                            temp.circle.copy(circle.circle)
                            temp.circle.notifyChange()
                            temp = circle
                        }

                        val last = lineSnake.last()
                        //如果有食物
                        if (hasFood) {
                            //过滤掉除了条件以外的元素
                            val blankBox = table.filter { it.type == TYPE.NONE }
                        } else {
                            lineSnake.remove(last)//去尾
                            last.circle.clear()//不要尾部的数据了
                            last.circle.notifyChange()
                        }
                        lineSnake.add(0, head)//加头

                        //捕获中断退出贪吃蛇程序
                        try {
                            Thread.sleep(500)
                        } catch (e: Exception) {
                            break
                        }

                    }//***贪吃蛇循环 - 尾部***
                }.apply {
                    threadsSnake.add(this)
                }.start()

            }
        }

        private lateinit var rvParent: RecyclerView
        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            rvParent = recyclerView
            rvParent.itemAnimator?.changeDuration = 0

        }
    }
    //**************************************** Adapter End ****************************************

    class Holder(rvParent: View) : RecyclerView.ViewHolder(rvParent) {
        val tvCircle: TextView = rvParent.findViewById(R.id.circle_tvTxt)
        val tvText2: TextView = rvParent.findViewById(R.id.circle_tvTxt2)
        val tvText3: TextView = rvParent.findViewById(R.id.circle_tvTxt3)
    }
}


//region Self Define
//*************************************** Self Define **********************************************
open class Circle(
    var textColor: Int = Color.WHITE,
    var type: TYPE = TYPE.NONE,
    var show: Boolean = false,
    var text: String = "",
    var direS_int: Int = 0,
    var tvText: TextView? = null,
    var tvText2: TextView? = null,
    var index: Int = -1
) {

    /**拷贝数据到本类中*/
    fun copy(circle: Circle) {
        textColor = circle.textColor
        type = circle.type
        show = circle.show
        text = circle.text
        direS_int = circle.direS_int
    }

    /**清空本类的数据*/
    fun clear() {
        textColor = 0
        type = TYPE.NONE
        show = false
        text = ""
        direS_int = 0
    }

    override fun toString(): String = "index=$index  type=$type  show=$show  text=\"$text\""

    enum class TYPE {
        NONE, WHITE, BLACK, RED, TEST, FOOD, EMPTY;

        /**获取该类型的资源*/
        val resource: Int
            get() {
                return when (this) {
                    BLACK -> R.drawable.ic_bg_circle_black
                    WHITE -> R.drawable.ic_bg_circle_white
                    TEST -> R.drawable.ic_bg_circle_blue
                    FOOD -> R.drawable.ic_bg_food
                    RED -> R.drawable.ic_bg_circle_red
                    NONE -> R.drawable.ic_bg_rectangle
                    else -> R.drawable.ic_bg_rectangle
                }
            }

        /**获取该类型的颜色*/
        val color: Int
            get() {
                return when (this) {
                    WHITE -> Color.WHITE
                    BLACK -> Color.BLACK
                    TEST -> Color.BLUE
                    else -> Color.YELLOW
                }
            }

        override fun toString(): String = name

        val name1: String
            get() {
                return when (this) {
                    WHITE -> "白棋"
                    BLACK -> "黑棋"
                    TEST -> "测试"
                    else -> "空"
                }
            }

        /**改变为相反的类型棋子*/
        val alter: TYPE
            get() {
                return when (this) {
                    WHITE -> BLACK
                    BLACK -> WHITE
                    else -> TEST
                }
            }
    }
}

class CircleD(var circle: Circle = Circle(), var direction: Direction = Direction.None) {

    constructor(circleD: CircleD) : this(circleD.circle, circleD.direction)

    /**拷贝数据到自己*/
    fun copy(circleD: CircleD): Unit {
        circle = circleD.circle
        direction = circleD.direction
    }

    override fun toString(): String = "[${circle.index}] $direction"
}

/**方向枚举类，简化枚举*/
enum class DirectionS(val value: Int) {
    /**─*/
    Horizontal(1),
    /**│*/
    Vertical(2),
    /**╲*/
    CantLeft(4),
    /**╱*/
    CantRight(8),
    /**╳*/
    None(0);

    override fun toString(): String = "$name1  binary=$strBinary"

    val strBinary: String
        get() {
            var num = value
            val sb = StringBuffer()
            while (num != 0) {
                val i = num % 2
                sb.insert(0, i)
                num /= 2
            }
            return sb.toString()
        }

    val name1: String
        get() {
            return when (this) {
                Horizontal -> "─"
                Vertical -> "│"
                CantLeft -> "╲"
                CantRight -> "╱"
                else -> "╳"
            }
        }
}

/**方向枚举类*/
enum class Direction {
    None,
    Left,
    Top,
    Right,
    Bottom,

    LeftTop,
    LeftBottom,
    RightTop,
    RightBottom;

    /**取反操作*/
    operator fun not(): Direction {
        return when (this) {
            Left -> Right
            Top -> Bottom
            LeftTop -> RightBottom
            LeftBottom -> RightTop

            Right -> Left
            Bottom -> Top
            RightBottom -> LeftTop
            RightTop -> LeftBottom
            else -> None
        }
    }

    /**Direction转换DirectionS后的值*/
    val direS: DirectionS
        get() {
            return when (this) {
                Left, Right -> DirectionS.Horizontal
                Top, Bottom -> DirectionS.Vertical
                LeftTop, RightBottom -> DirectionS.CantLeft
                LeftBottom, RightTop -> DirectionS.CantRight
                else -> DirectionS.None
            }
        }

    override fun toString(): String {
        val strDirection = when (this) {
            Left -> "←"
            Top -> "↑"
            Right -> "→"
            Bottom -> "↓"
            LeftTop -> "↖"
            LeftBottom -> "↙"
            RightTop -> "↗"
            RightBottom -> "↘"
            else -> "㊣"
        }
        return "$strDirection"
    }
}
//endregion

typealias  ArrayLines = ArrayList<ArrayList<CircleD>>
typealias  ArrayLine = ArrayList<CircleD>
