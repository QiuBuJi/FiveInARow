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

const val TAG = "msg_mine"

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
                    setOnClickListener {
                        //设置不能在同一个地方重复下棋
                        if (circle.show) {
                            showTableLines(circle.type)
                            return@setOnClickListener
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
                }

                //显示下标
                tvText3.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePieces * 0.16f)
                tvText3.text = position.toString()
            }
        }

        private fun showTableLines(type: Circle.TYPE) {
            val tableLines =
                getTableLines().filter { it[0].circle.type == type } as ArrayList<ArrayList<CircleD>>
            tableLines.sortByDescending { it.size }

            //                    detect(circle)
            if (tableLines.isNotEmpty()) {
                Thread {
                    for (line in tableLines) {
                        for (circleD in line) {
                            handler.send {
                                circleD.circle.tvText2?.text = "●"
                                circleD.circle.tvText2?.setTextColor(circleD.circle.type.alter.color)
                            }
                        }

                        val duration: Long = 300
                        Thread.sleep(duration)
                        for (circleD in line) {
                            handler.send {
                                circleD.circle.tvText2?.text = ""
                                circleD.circle.tvText2?.setTextColor(circleD.circle.type.color)
                            }
                        }
                        Thread.sleep(duration)
                    }

                }.start()
            }
        }

        private fun runAI(type: Circle.TYPE) {
            if (currentType != type) return

            val thread = Thread {
                Thread.sleep(500)
                Log.d(TAG, "------------------------------------------------------------------")

                val group = getTableLines().groupBy { it.first().circle.type }

                //排除不属于自己的类型
                val linesMyOwn = (group[type] ?: ArrayLines()) as ArrayLines
                val linesEnemy = (group[type.alter] ?: ArrayLines()) as ArrayLines
                linesMyOwn.sortByDescending { it.size }//大到小排序
                linesEnemy.sortByDescending { it.size }//大到小排序

                var firstEnemy: ArrayList<CircleD>? = null
                var firstMyOwn: ArrayList<CircleD>? = null

                try {
                    firstEnemy = linesEnemy.first()
                    firstMyOwn = linesMyOwn.first()
                } catch (e: Exception) {
                }

                //去除无意义直线
                val deadLines = linesMyOwn.removeDeadLines()
                linesEnemy.removeDeadLines()

                when {
                    firstEnemy != null && (firstEnemy.size > 3 || linesMyOwn.isEmpty()) -> {
                        Log.d(TAG, "runAI: firstEnemy.size > 3 || linesMyOwn.isEmpty()")

                        when {
                            //敌人棋子长度是3了，不得不扼杀它
                            firstEnemy.size >= 3 || deadLines.isEmpty() -> {
                                val remainBlank = firstEnemy.getRemainBlanks()

                                if (remainBlank.isNotEmpty()) {
                                    val circleD = remainBlank.first()
                                    handler.send { circleD.circle.tvText?.performClick() }
                                    Log.d(TAG, "runAI: has remain")
                                } else {
                                    Log.d(TAG, "runAI: no more remain")
                                }
                            }
                            //我方没有现成的“直线”或者“自由点”可用
                            linesMyOwn.isEmpty() -> {
                                if (deadLines.isNotEmpty()) {
                                    Log.d(TAG, "runAI: deadLines has data")
                                    var random = deadLines.random()
                                    val availableLines = random.getAvailableLines()
                                    availableLines.removeIf { it.size < 5 }
                                    random = availableLines.random()

                                    var circleD: CircleD? = null
                                    for (temp in random) {
                                        if (temp.circle.type != Circle.TYPE.NONE) break
                                        circleD = temp
                                    }
                                    if (circleD == null) circleD = random[1]
                                    handler.send { circleD.circle.tvText?.performClick() }

                                } else {
                                    Log.d(TAG, "runAI: deadLines not data")

                                }

                            }
                        }


                    }
                    else -> {
                        Log.d(TAG, "runAI: normal process")

                        for (line in linesMyOwn) {
                            val temp = ArrayList<CircleD>()

                            if (line.size == 1) {
                                val remainBlank = line.getRemainBlanks()
                                if (remainBlank.isNotEmpty()) temp.add(remainBlank.random())
                            } else {
                                for (circleD in line) {
                                    val surrounds = circleD.circle.getSurrounds {
                                        it.direction.direS == circleD.direction.direS && it.circle.type == Circle.TYPE.NONE
                                    }
                                    temp.addAll(surrounds)
                                }
                            }

                            if (temp.isNotEmpty()) {
                                Log.d(TAG, "runAI: has line data")
                                val random = temp.random()
                                handler.send { random.circle.tvText?.performClick() }
                                break
                            } else {
                                Log.d(TAG, "runAI: has not line data")

                            }
                        }
                    }
                }
                Log.d(TAG, "------------------------------------------------------------------")
            }
            thread.start()
        }

        /**获取该直线周围有用的直线*/
        private fun ArrayLine.getAvailableLines(): ArrayLines {
            val linesOut = ArrayLines()

            for (circleD in this) {
                val surrounds = circleD.circle.getSurrounds {
                    it.circle.type != circleD.circle.type.alter && it.direction.direS != circleD.direction.direS
                }

                val lines = ArrayLines()
                for (surround in surrounds) {
                    val directionLine = surround.getDirectionLine {
                        it.circle.type != circleD.circle.type.alter && it.direction.direS != circleD.direction.direS
                    }
                    lines.add(directionLine)
                }
                val connectLines = circleD.circle.connectLines(lines)
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
                val remainBlanks = next.getRemainBlanks()
                val finalSize = remainBlanks.size + next.size
                if (finalSize < 5) {
                    deadLines.add(next)
                    iterator.remove()
                }
            }
            return deadLines
        }

        /**获取该直线还能继续扩展的地方*/
        private fun ArrayLine.getRemainBlanks(): ArrayList<CircleD> {
            val line = this
            return if (line.size == 1) {
                //取周围的空白格子
                line.first().circle.getSurrounds { it.circle.type == Circle.TYPE.NONE }
            } else {
                val first = line.first()
                val blanks = ArrayList<CircleD>()

                //取该方向的空白格子
                first.traverseDirection {
                    when (it.circle.type) {
                        Circle.TYPE.NONE -> {
                            blanks.add(it)
                            true
                        }
                        first.circle.type -> true
                        else -> false
                    }
                }
                //取反方向的空白格子
                first.direction = !first.direction
                first.traverseDirection {
                    when (it.circle.type) {
                        Circle.TYPE.NONE -> {
                            blanks.add(it)
                            true
                        }
                        first.circle.type -> true
                        else -> false
                    }
                }
                blanks
            }
        }

        /**给直线每个格子设置文字*/
        private fun insertText(line: ArrayList<CircleD>, txt: String): Unit {
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
        private fun gameSnake(it: ArrayList<ArrayList<CircleD>>) {
            for (line in it) {
                Thread {
                    var direction: Direction = Direction.Right

                    //贪吃蛇“结束”程序
                    fun deathProgress(line: java.util.ArrayList<CircleD>) {
                        //显示死时候的样貌
                        for (circle in line) {
                            circle.circle.textColor = Color.RED
                            circle.circle.notifyChange()
                        }
                        insertText(line, "我死了********")
                        Thread.sleep(2000)

                        //复原数据
                        for (circle in line) {
                            circle.circle.clear()
                            circle.circle.notifyChange()
                        }
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
                            deathProgress(line)
                            break
                        }
                        //没有方向可以行走，死亡程序开始
                        if (surroundBoxes.isEmpty()) {
                            deathProgress(line)
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
        NONE, WHITE, BLACK, TEST, FOOD;

        /**获取该类型的资源*/
        val resource: Int
            get() {
                return when (this) {
                    BLACK -> R.drawable.ic_bg_circle_black
                    WHITE -> R.drawable.ic_bg_circle_white
                    TEST -> R.drawable.ic_bg_circle_blue
                    FOOD -> R.drawable.ic_bg_food
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

class CircleD(val circle: Circle, var direction: Direction) {

    override fun toString(): String = "direction = $direction   index = ${circle.index}"
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
        return "$strDirection  ($name)"
    }
}
//endregion

typealias  ArrayLines = ArrayList<ArrayList<CircleD>>
typealias  ArrayLine = ArrayList<CircleD>
