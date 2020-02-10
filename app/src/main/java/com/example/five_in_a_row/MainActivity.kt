package com.example.five_in_a_row

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.StringBuilder

const val TAG = "信息"

class MainActivity : AppCompatActivity() {
    private var adapter: Adapter? = null

    //region Companion
    companion object {
        lateinit var mTvTitle: TextView
        lateinit var mTvCircle: TextView
        val sizeScreen = Point()
        var heightMain: Int = 0
        var typeDefault: Circle.TYPE = Circle.TYPE.WHITE
    }
    //endregion

    //region onCreate
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //提取控件到全局变量
        mTvTitle = main_tvTitle
        mTvCircle = main_tvCircle

        heightMain = main_clMain.height
        windowManager.defaultDisplay.getSize(sizeScreen)//取屏幕尺寸

        //init others

        main_btClear.setOnClickListener { swapAdapter(point) }//监听器，清空棋盘
        main_btClear.performClick()//初始化棋盘
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

            add(group3, 4, 0, "关于")
        }
        return super.onCreateOptionsMenu(menu)
    }

    private var point: Point = Point(8, 14)//初始棋盘格数
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        item?.run {
            when (groupId) {
                //设置哪种类型的棋先下
                group1 -> {
                    when (itemId) {
                        1 -> adapter!!.currentType = Circle.TYPE.WHITE
                        2 -> adapter!!.currentType = Circle.TYPE.BLACK
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
        private val table = ArrayList<Circle>()

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
            handler.send { notifyItemChanged(this.index) }
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

        private fun CircleD.getDirectionLine(action: (circleD: CircleD) -> Boolean = { it.circle.type != Circle.TYPE.NONE }): ArrayList<CircleD> {
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
        private fun Circle.getLines(action: (circleD: CircleD) -> Boolean = { true }): ArrayList<ArrayList<CircleD>> {
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
            lines: ArrayList<ArrayList<CircleD>>,
            mark: Boolean = false
        ): ArrayList<ArrayList<CircleD>> {
            val linesOut = ArrayList<ArrayList<CircleD>>()

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
                if (currCircle.type == Circle.TYPE.NONE) continue//避开空格子

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
            return lines
        }

        //endregion
        //******************************************************************************************
        /**当前该下棋的类型*/  //这个初始化不管用
        var currentType: Circle.TYPE = typeDefault
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
                    setOnClickListener { circle.clicked() }
                }

                //显示下标
                tvText3.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePieces * 0.16f)
                tvText3.text = position.toString()
            }
        }

        /**格子被单击*/
        private fun Circle.clicked() {
            val circle = this

            //设置不能在同一个地方重复下棋
            if (circle.show) {
//                showTableLines(circle.type)
//                circle.getLines()
                return
            }
            //保存上一次，下的是什么棋

            circle.show = true
            circle.type = currentType
            currentType = currentType.alter
            typeDefault = currentType

            //检查棋盘
            tableChecking(circle)
            //通知该位置刷新显示的数据
            circle.notifyChange()

            runAI(Circle.TYPE.BLACK)
        }

        private fun showTableLines(type: Circle.TYPE) {
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
        private fun runAI(type: Circle.TYPE) {
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
                var myOwnThreat: ArrayLine? = null

                //去除无意义直线
                val deadLinesMyOwn = linesMyOwn.removeDeadLines()
                val deadLinesEnemy = linesEnemy.removeDeadLines()

                try {
                    enemyThreat = linesEnemy.first()
                    myOwnThreat = linesMyOwn.first()
                } catch (e: Exception) {
                }

                //直线两端的空格子
                var lineEndBlank = ArrayLine()

                //AI逻辑代码部分---------------------------------------------------------------------
                when {
                    enemyThreat != null && (enemyThreat.size >= 3 || linesMyOwn.isEmpty()) -> {

                        val endOne = CircleD()
                        //enemyThreat直线长度大于等于3就满足条件，如果它是半阻塞状态则不能满足
                        val halfBlocked = enemyThreat.isHalfBlocked(endOne)//是否为半开闭区间
                        val stateLength = enemyThreat.size >= 3 && !halfBlocked//条件：直线长度大于2，且为开放状态
                        //enemyThreat直线长度>=4,已经迫在眉睫，敌人快赢了！
                        val stateLengthThreat = enemyThreat.size >= 4
                        val empty = deadLinesMyOwn.isEmpty()


                        logd("敌人长度: ${enemyThreat.size}  处于${if (halfBlocked) "半" else "开"}放状态")
                        when {
                            //敌人棋子长度是3了，不得不扼杀它 ****************************************
                            stateLength || stateLengthThreat || empty -> {

                                val edgeBlanks = ArrayLine()
                                //myOwnThreat自己的直线，如果长度>=3占优势，满足条件，给自己优势的直线继续扩展
                                val myOwnSize = myOwnThreat?.size ?: 0
                                var isIntrude = myOwnSize >= 3 && enemyThreat.size <= 3//进攻的条件
                                if ((enemyThreat.size == 3 && halfBlocked) ||
                                    myOwnSize >= 4
                                ) isIntrude = true


                                //根据条件，是给增强自己，还是压制敌人
                                lineEndBlank =
                                    if (isIntrude) {
                                        logd("增强自己")

                                        myOwnThreat!!.getLineEndBlanks(edgeBlanks)//增强自己
                                    } else //增强自己
                                    {
                                        logd("压制敌人")

                                        enemyThreat.getLineEndBlanks(edgeBlanks)//压制敌人
                                    }

                                if (edgeBlanks.isNotEmpty()) {
                                    lineEndBlank = edgeBlanks

                                    logd("可选下棋点： $edgeBlanks")
                                }
                            }
                            linesMyOwn.isEmpty() -> {
                                //从自己死胡同了的直线，发展分支
                                if (deadLinesMyOwn.isNotEmpty()) {
                                    logd("有封闭直线数据")

                                    val deadLine = deadLinesMyOwn.random()//随机取一条死直线
                                    val liveLines = deadLine.getLiveLines()//取活直线
                                    liveLines.removeIf { it.size < 5 }//去除没有赢意图的直线
                                    liveLines.sortByDescending { it.size }//从大到小排序

                                    if (liveLines.isNotEmpty()) {
                                        val liveLine = liveLines.first()
                                        liveLine.removeIf { it.circle.type != Circle.TYPE.NONE }
                                        lineEndBlank = liveLine

                                        logd("得到开放直线")
                                    } else {
                                        logd("无开放直线")
                                    }

                                } else {
                                    logd("无封闭直线数据")
                                }

                            }
                            //其它状况**************************************************************
                            else -> {
                                //随机，进攻还是防御
                                val isIntrude = arrayListOf(true, false).random()
                                val edgeBlanks = ArrayLine()

                                lineEndBlank =
                                    if (isIntrude) myOwnThreat!!.getLineEndBlanks(edgeBlanks)//增强自己
                                    else enemyThreat.getLineEndBlanks(edgeBlanks)//压制敌人

                                if (edgeBlanks.isNotEmpty()) lineEndBlank = edgeBlanks

                                logd("随机状态: ${if (isIntrude) "进攻" else "防御"}")
                            }
                        }


                    }
                    //分支 *************************************************************************
                    else -> {
                        logd("增强自己")

                        //扩展我方最有潜力的直线，直线越长权重越大
                        for (line in linesMyOwn) {
                            //直线长度等于1时的分支***************************************************
                            if (line.size == 1) {
                                logd("单独棋子: ${line.first()}")

                                val liveLines = line.getLiveLines()
                                liveLines.sortByDescending { it.size }//给自己从大到小排序
                                for (liveLine in liveLines) liveLine.sortBy { it.circle.index }

                                //闪烁*******
                                for (liveLine in liveLines) {
                                    val type =
                                        if (liveLine.size + 1 < 5) Circle.TYPE.RED
                                        else Circle.TYPE.TEST
                                    val lineTemp = ArrayLine(liveLine)
                                    lineTemp.removeIf { it.circle.type != Circle.TYPE.NONE }//去除非空棋子
                                    lineTemp.flashCircle(400, 2, type)
                                }

                                liveLines.removeIf { it.size < 5 }//不要最终长度不能达到5颗棋子的直线

                                val liveLine = ArrayLine()
                                val temp = ArrayLine()
                                var breakIt = false

                                //这条线的本棋子大于2，则权利更大
                                for (line in liveLines) {
                                    temp.clear()
                                    for (circleD in line) {
                                        if (circleD.circle.type != Circle.TYPE.NONE) {
                                            temp.reverse()
                                            if (temp.size > 4) {
                                                val dropLast = temp.dropLast(temp.size - 4)
                                                liveLine.addAll(dropLast)
                                            } else liveLine.addAll(temp)
                                            breakIt = true
                                            break
                                        }
                                        temp.add(circleD)
                                    }
                                    if (breakIt) break
                                }


                                if (liveLines.isNotEmpty()) {
                                    if (liveLine.isEmpty()) liveLine.addAll(liveLines.random())
                                    liveLine.removeIf { it.circle.type != Circle.TYPE.NONE }//去除非空棋子
                                    val blank = liveLine.random()
                                    lineEndBlank.add(blank)

                                    liveLine.flashCircle(600, 3)
                                    logd("有开放直线数据  $liveLine")
                                    break
                                }

                            }
                            //直线长度大于1的分支*****************************************************
                            else {
                                val lineEndBlanks = line.getLineEndBlanks()//取直线两端空白棋子
                                lineEndBlank.addAll(lineEndBlanks)

                                logd("线段: ${line.first()} ${line.last()}")
                                break
                            }
                        }

                        //else end
                    }
                }

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

            }
            thread.start()
        }

        /**让直线内的数据闪烁的显示*/
        private fun ArrayLine.flashCircle(
            duration: Int = 1000, times: Int = 3, type: Circle.TYPE = Circle.TYPE.TEST
        ) {

            val edgeBlanks: ArrayLine = this
            val duration = duration / 2 / times
            for (index in 1..times) {
                flash(edgeBlanks, type, duration)
                flash(edgeBlanks, Circle.TYPE.NONE, duration)
            }
        }

        /**1个闪烁周期*/
        private fun flash(edgeBlanks: ArrayLine, type: Circle.TYPE, duration: Int = 1000) {
            for (edgeBlank in edgeBlanks) {
//                if (edgeBlank.circle.type == Circle.TYPE.BLACK || edgeBlank.circle.type == Circle.TYPE.WHITE) continue//只要空的
                edgeBlank.circle.type = type
                edgeBlank.circle.notifyChange()
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

        /**是否一端是阻塞的*/
        private fun ArrayLine.isHalfBlocked(circleD: CircleD? = null): Boolean {
            val first = first()

            if (first.getDirectionBlanks(circleD).size == 0) return true
            first.direction = first.direction.not()
            if (first.getDirectionBlanks(circleD).size == 0) return true
            return false
        }

        /**获取该直线周围有用的直线*/
        private fun ArrayLine.getLiveLines(): ArrayLines {
            val linesOut = ArrayLines()

            for (circleD in this) {
                //取环绕该点的棋子
                val surrounds = circleD.circle.getSurrounds {
                    //条件：不能为敌方棋子，也不能是相同方向的棋子
                    it.circle.type != circleD.circle.type.alter && it.direction.direS != circleD.direction.direS
                }
                val lines = ArrayLines()

                //取发散的直线
                for (surround in surrounds) {
                    val directionLine = surround.getDirectionLine {
                        //条件：不能为敌方棋子，也不能是相同方向的棋子
                        it.circle.type != circleD.circle.type.alter //&& it.direction.direS != circleD.direction.direS
                    }
                    lines.add(directionLine)
                }
                val connectLines = circleD.circle.connectLines(lines)

                //去除包含自己
                for (connectLine in connectLines) {
                    connectLine.removeIf { it.circle == circleD.circle }
                }
                linesOut.addAll(connectLines)
            }
            return linesOut
        }

        /**去除没有胜利意义的直线*/
        private fun ArrayLines.removeDeadLines(): ArrayLines {
            val iterator = iterator()
            val deadLines = ArrayLines()

            while (iterator.hasNext()) {
                val next = iterator.next()
                val remainBlanks = next.getLineEndBlanks()
                val finalSize = remainBlanks.size + next.size

                if (finalSize < 5) {
                    deadLines.add(next)
                    iterator.remove()
                }
            }
            return deadLines
        }

        /**获取该直线还能继续扩展的地方*/
        private fun ArrayLine.getLineEndBlanks(edgeBlanks: ArrayLine? = null): ArrayLine {
            val line = this
            return if (line.size == 1) {
                //取周围的空白格子
                val surrounds =
                    line.first().circle.getSurrounds { it.circle.type == Circle.TYPE.NONE }
                edgeBlanks?.addAll(surrounds)
                surrounds
            } else {
                val first = line.first()
                val blanks = ArrayList<CircleD>()

                //取该方向的空白格子
                blanks.addAll(first.getDirectionBlanks()
                                  .apply { if (isNotEmpty()) edgeBlanks?.add(first()) })
                //取反方向的空白格子
                first.direction = !first.direction
                blanks.addAll(first.getDirectionBlanks()
                                  .apply { if (isNotEmpty()) edgeBlanks?.add(first()) })
                blanks
            }
        }

        /**取单方向的空白格子*/
        private fun CircleD.getDirectionBlanks(endOne: CircleD? = null): ArrayLine {
            val blanks = ArrayLine()
            var isAdd = true

            this.traverseDirection { it ->
                when (it.circle.type) {
                    Circle.TYPE.NONE -> {
                        blanks.add(it)
                        isAdd = false
                        true
                    }
                    circle.type -> {
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
        private fun tableChecking(pCircle: Circle) {
            val detectWon = pCircle.detectWon()

            detectWon?.let {
                Thread {
                    val strWin = "太棒了你！！！！！"
                    val getC = strWin.iterator()
                    food = pCircle.type.alter

                    //有赢的数据，则执行赢程序 *******************************************************
                    for (line in it) {
                        for (circle in line) {
                            circle.circle.run {
                                text = if (getC.hasNext()) getC.next().toString() else ""
                                textColor = circle.circle.type.color
                                type = Circle.TYPE.TEST
                                notifyChange()
                            }
                            Thread.sleep(200)
                        }
                    }

                    //加入贪吃蛇
                    gameSnake(it)
                }.start()

            }
        }

        private var food = Circle.TYPE.FOOD

        val threadsSnake = ArrayList<Thread>()
        //贪吃蛇游戏代码部分 ************************************************************
        private fun gameSnake(it: ArrayLines) {
            for (line in it) {
                Thread {
                    var direction: Direction = Direction.Right

                    //贪吃蛇“结束”程序
                    fun deathProgress(line: java.util.ArrayList<CircleD>): Boolean {
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
                        var head = line.first()
                        if (head.circle.type == Circle.TYPE.NONE) break
                        head.circle.textColor = Color.RED

                        //行走方向获取
                        var surroundBoxes = head.circle.getSurrounds {
                            it.circle.type == food || it.circle.type == Circle.TYPE.NONE &&
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
                                it.circle.type == Circle.TYPE.NONE || it.circle.type == food
                            }

                        var random = try {
                            surroundBoxes.random()//随机获取方向
                        } catch (e: Exception) {
                            //没有方向可以行走，死亡程序开始
                            if (deathProgress(line)) break
                            break
                        }
                        //没有方向可以行走，死亡程序开始
                        if (surroundBoxes.isEmpty()) {
                            if (deathProgress(line)) break
                            break
                        }


                        var hasDir = false
                        for (box in surroundBoxes) {
                            box.traverseDirection {
                                if (it.circle.type == food) {
                                    random = box
                                    hasDir = true
                                    false
                                } else it.circle.type == Circle.TYPE.NONE
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
                        for (index in 1 until line.size) {
                            val circle = line[index]

                            temp.circle.copy(circle.circle)
                            temp.circle.notifyChange()
                            temp = circle
                        }

                        val last = line.last()
                        //如果有食物
                        if (hasFood) {
                            //过滤掉除了条件以外的元素
                            val blankBox = table.filter { it.type == Circle.TYPE.NONE }
                        } else {
                            line.remove(last)//去尾
                            last.circle.clear()//不要尾部的数据了
                            last.circle.notifyChange()
                        }
                        line.add(0, head)//加头

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
        NONE, WHITE, BLACK, RED, TEST, FOOD;

        /**获取该类型的资源*/
        val resource: Int
            get() {
                return when (this) {
                    BLACK -> R.drawable.ic_bg_circle_black
                    WHITE -> R.drawable.ic_bg_circle_white
                    TEST -> R.drawable.ic_bg_circle_blue
                    FOOD -> R.drawable.ic_bg_food
                    RED -> R.drawable.ic_bg_circle_red
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
