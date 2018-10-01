package com.hp.android.angel.arcoreexample

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.view.MotionEvent
import android.widget.Toast
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.Light
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment

import com.hp.android.angel.arcoreexample.Configuration.Companion.ROW_NUM
import com.hp.android.angel.arcoreexample.Configuration.Companion.COL_NUM
import com.hp.android.angel.arcoreexample.Configuration.Companion.START_LIVES
import com.hp.android.angel.arcoreexample.Configuration.Companion.MIN_MOVE_DELAY_MS
import com.hp.android.angel.arcoreexample.Configuration.Companion.MAX_MOVE_DELAY_MS
import com.hp.android.angel.arcoreexample.Configuration.Companion.MIN_PULL_DOWN_DELAY_MS
import com.hp.android.angel.arcoreexample.Configuration.Companion.MAX_PULL_DOWN_DELAY_MS
import com.hp.android.angel.arcoreexample.Configuration.Companion.MOVES_PER_TIME

import com.hp.android.angel.arcoreexample.DroidPosition.DOWN
import com.hp.android.angel.arcoreexample.DroidPosition.MOVING_DOWN

class Main2Activity : AppCompatActivity() {

    private lateinit var arFragment: ArFragment

    private lateinit var scoreboard: ScoreboardView

    private var gameHandler = Handler()

    // Model
    private var droidRenderable: ModelRenderable? = null

    // Scoreview
    private var scoreboardRenderable: ViewRenderable? = null

    // Light
    private var failLight: Light? = null

    // Represent the grid of droids
    private var grid = Array(ROW_NUM) { arrayOfNulls<TranslatableNode>(COL_NUM) }
    // Indicate whether the game board is initialized
    private var initialized = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        arFragment = supportFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment

        initResources()


        arFragment.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, _: MotionEvent ->
            if (initialized) {
                // 1: Handle a failed hit on a droid, and return.
                // Already initialized!
                // When the game is initialized and user touches without
                // hitting a droid, remove 50 points
                failHit()
                return@setOnTapArPlaneListener
            }

            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) {
                // 2: Alert the user if they’ve picked a bad plane for the game, and return.
                // Only HORIZONTAL_UPWARD_FACING planes are good to play the game
                // Notify the user and return
                "Find an HORIZONTAL and UPWARD FACING plane!".toast(this)
                return@setOnTapArPlaneListener
            }

            if (droidRenderable == null || scoreboardRenderable == null || failLight == null){
                // 3: Return if not all renderable objects have been initialized.
                // Every renderable object must be initialized
                // On a real world/complex application
                // it can be useful to add a visual loading
                return@setOnTapArPlaneListener
            }

            val spacing = 0.3F

            val anchorNode = AnchorNode(hitResult.createAnchor())

            anchorNode.setParent(arFragment.arSceneView.scene)

            // 4: Set up droids on the plane.
            // Add N droid to the plane (N = COL x ROW)
            grid.matrixIndices { col, row ->
                val renderableModel = droidRenderable?.makeCopy() ?: return@matrixIndices
                TranslatableNode().apply {
                    setParent(anchorNode)
                    renderable = renderableModel
                    addOffset(x = row * spacing, z = col * spacing)
                    grid[col][row] = this
                    this.setOnTapListener { _, _ ->
                        // TODO: You hit a droid!
                        if (this.position != DOWN) {
                            // Droid hit! assign 100 points
                            scoreboard.score += 100
                            this.pullDown()
                        } else {
                            // When player hits a droid that is not up
                            // it's like a "miss", so remove 50 points
                            failHit()
                        }
                    }
                }
            }

            // 5: Add the scoreboard view to the plane.
            // Add the scoreboard view to the plane
            val renderableView = scoreboardRenderable ?: return@setOnTapArPlaneListener
            TranslatableNode()
                    .also {
                        it.setParent(anchorNode)
                        it.renderable = renderableView
                        it.addOffset(x = spacing, y = .6F)
                    }

            // 6: Add a light to the game.
            // Add a light
            Node().apply {
                setParent(anchorNode)
                light = failLight
                localPosition = Vector3(.3F, .3F, .3F)
            }

            // 7
            initialized = true
        }

    }

    private fun initResources() {
        // Create a droid renderable (asynchronous operation,
        // result is delivered to `thenAccept` method)
        ModelRenderable.builder()
                .setSource(this, Uri.parse("andy.sfb"))
                .build()
                .thenAccept { droidRenderable = it }
                .exceptionally { it.toast(this) }





        // Create scoreview
        scoreboard = ScoreboardView(this)

        scoreboard.onStartTapped = {
            // Reset counters
            scoreboard.life = START_LIVES
            scoreboard.score = 0

            // Start the game!
            gameHandler.post {
                repeat(MOVES_PER_TIME) {
                    gameHandler.post(pullUpRunnable)
                }
            }
        }

        // create a scoreboard renderable (asynchronous operation,
        // result is delivered to `thenAccept` method)
        ViewRenderable.builder()
                .setView(this, scoreboard)
                .build()
                .thenAccept {
                    it.isShadowReceiver = true
                    scoreboardRenderable = it
                }
                .exceptionally { it.toast(this) }



        // Creating a light is NOT asynchronous
        failLight = Light.builder(Light.Type.POINT)
                .setColor(Color(android.graphics.Color.RED))
                .setShadowCastingEnabled(true)
                .setIntensity(0F)
                .build()
    }

    private fun failHit() {
        scoreboard.score -= 50
        scoreboard.life -= 1
        failLight?.blink()

        if (scoreboard.life <= 0) {
            // Game over
            gameHandler.removeCallbacksAndMessages(null)
            /*
            * Whenever the player fails a hit, if its life counter is equal to zero or less,
            * reset every droid on the grid by calling the pullDown.
            * */
            grid.flatMap { it.toList() }
                    .filterNotNull()
                    .filter { it.position != DOWN && it.position != MOVING_DOWN }
                    .forEach { it.pullDown() }
        }
    }


    private val pullUpRunnable: Runnable by lazy {
        Runnable {
            // Check if the game is completed.
            if (scoreboard.life > 0) {
                grid.flatMap { it.toList() }
                        .filter { it?.position == DOWN }
                        .run { takeIf { size > 0 }?.getOrNull((0..size).random()) }
                        ?.apply {
                            // Pull up a random droid renderable from the grid.
                            pullUp()
                            // ull the same droid down after a random delay.
                            val pullDownDelay = (MIN_PULL_DOWN_DELAY_MS..MAX_PULL_DOWN_DELAY_MS).random()
                            gameHandler.postDelayed({ pullDown() }, pullDownDelay)
                        }

                // If player has at least one life, start itself over again after a random delay.
                // Delay between this move and the next one
                val nextMoveDelay = (MIN_MOVE_DELAY_MS..MAX_MOVE_DELAY_MS).random()
                gameHandler.postDelayed(pullUpRunnable, nextMoveDelay)
            }
        }
    }
}

/*
When the player taps the plane for the first time, Sceneform will instantiate the whole game, so
you’ll set an onTouchListener on the plane.
When the player taps on the Start button, the game will begin; the click listener is handled by the onClickListener of ScoreBoardView.
If the player hits a droid, he or she will gain 100 points, so you’ll intercept the onTouchEvent on the droid.
If the player misses the droid, he or she will lose a life and 50 points; you’ll need to detect a tap
on the plane so you can reuse the existing listener.

If the droid is down, it counts as a missed hit, so you remove 50 points and a life from the player.
If it’s up, add 100 points to the player.

*/