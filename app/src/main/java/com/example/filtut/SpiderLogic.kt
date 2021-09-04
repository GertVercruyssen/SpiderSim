package com.example.filtut

import android.util.Log
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.utils.Float3
import kotlin.math.*
import kotlin.random.Random

/**
 * Spider : crawls on flat surface and jump when touch is detected
 */
class SpiderLogic(startx: Float, starty: Float, startz: Float, scale: Float){

    enum class Status {
        IDLE,MOVING,JUMPSTART,JUMPING,JUMPEND;
    }

    lateinit var animator : Animator
    var scale = scale
    private val jumpspeed = 20.0f
    private val jumpstrength = 15.0f
    private val gravity = 29.81f
    private val walkspeed = 10.0f
    var position: Float3 = Float3(startx, starty, startz)
    private var vertspeed = 0.0f
    private var rotationspeed = 0.0f
    var rotation = 0.0f
    private var animationTimer : Double = 0.0 //time current animation is playing
    private var freezeTimer : Double = 0.0
    private var previousFrametime : Double = 0.0
    private var animationMaxTimer = 1.0f //time the current animation will take
    private var status = Status.IDLE
    var currentAnimationIndex = 2
    private var jump = false //set to true on touch and false when handled

    fun Step(totalTime : Double) : Double
    {
        var dTime = totalTime- previousFrametime
        previousFrametime = totalTime
        animationTimer +=dTime
        when (status) //spider behavior logic
        {
            Status.IDLE ->
            {
                if (jump)  {
                    DoJumpStartAnimation()
                }
                else {
                    //after idle is over, 70/30 chance to crawl around or do an idle animation
                    if (animationTimer >= animationMaxTimer){
                        animationTimer = animationMaxTimer.toDouble()
                        if(freezeTimer <= 0) {
                            if(random(0.0f,10.0f) > 7) {
                                DoIdleAnimation()
                            } else {
                                DoCrawlingAnimation();
                            }
                        }
                        else
                            freezeTimer -= dTime
                    }
                }
            }
            Status.MOVING ->
            {
                if (jump)  {
                    DoJumpStartAnimation()
                }
                else {
                    if (animationTimer > animationMaxTimer){
                        DoIdleAnimation()
                    }
                    else {
                        ApplyMovement(dTime, walkspeed)
                    }
                }
            }
            Status.JUMPSTART ->
            {
                if (animationTimer > animationMaxTimer){
                    DoJumpingAnimation();
                }
                ApplyMovement(dTime, jumpspeed)
            }
            Status.JUMPING ->
            {
                if (position.y < 2.3f){ //when we're about to land
                    DoJumpEndAnimation();
                }
                ApplyMovement(dTime, jumpspeed)
            }
            Status.JUMPEND ->
            {
                if (animationTimer > animationMaxTimer){
                    DoIdleAnimation()
                }
            }
        }
        return animationTimer
    }

    fun Touch(touchx : Float,touchy : Float) {
        if(status == Status.IDLE || status == Status.MOVING)
            jump = true
    }

    fun Move(touchx : Float,touchy : Float) {
        //TODO : Follow finger
        //raycast to see where we hit
    }

    fun CalcRotationAngle(spiderX :Float, spiderY :Float, targetX :Float, targetY :Float) : Float {
        var angle: Float = 0.0f
        if (spiderX == 0.0f) {
            if (spiderY>0)
                angle = Math.PI.toFloat()/2*-1
            if (spiderY<0)
                angle = Math.PI.toFloat()/2
        }
        if(spiderX<0.0f)
            angle = atan(-spiderY/-spiderX)
        if(spiderX>0.0f)
            angle = atan(-spiderY/-spiderX) +Math.PI.toFloat()
        return angle.toFloat()
    }
    fun CalcDistance(xdistance : Float, ydistance : Float) : Float {
        return sqrt(xdistance*xdistance+ydistance*ydistance)
    }

    fun GenerateSpeedAndDirection(): Float {
        var timetotarget = random(1.0f,2.0f) //between 1 and 2 second walking
        if(CalcDistance(position.x,position.z)>10) {  //too far fom center
            var angle = CalcRotationAngle(position.x, position.z, 0.0f, 0.0f) //Aim towards the center
            rotationspeed = (angle-rotation) / timetotarget
            //Log.i("foutje", "GenerateSpeedAndDirection: "+" xpos"+ position.x+" zpos"+ position.z+" angle"+ angle*180/3.14+" rotation"+ rotation*180/3.14)
        } else {
            rotationspeed = random(-1.0f,1.0f)
        }
        return timetotarget
    }

    fun DoIdleAnimation() {
        animationTimer = 0.0
        freezeTimer = 1.0
        status = Status.IDLE;
        //literally do nothing for a second or do a short animation
        var randomidle = random(0.0f,4.0f)
        if(randomidle<1) {
            currentAnimationIndex = 0 //fangs animation
            animationMaxTimer = animator.getAnimationDuration(currentAnimationIndex)
        }
        else if (randomidle<2) {
            currentAnimationIndex = 6 //wave animation
            animationMaxTimer = animator.getAnimationDuration(currentAnimationIndex)
        }
        else {
            animationMaxTimer = random(0.8f,2.5f)
            currentAnimationIndex = 1 //idle pose
        }
    }

    fun DoCrawlingAnimation() {
        animationTimer = 0.0
        status = Status.MOVING;
        animationMaxTimer = GenerateSpeedAndDirection()
        currentAnimationIndex = 5 //run animation
    }

    fun DoJumpStartAnimation() {
        animationTimer = 0.0
        status = Status.JUMPSTART;
        jump = false
        currentAnimationIndex = 4 //jumpstart animation
        animationMaxTimer = animator.getAnimationDuration(currentAnimationIndex)
        vertspeed = jumpstrength
        rotationspeed = 0.0f;
    }

    fun DoJumpingAnimation() {
        animationTimer = 0.0
        status = Status.JUMPING;
        currentAnimationIndex = 3 //jumping animation
        //speeds should still be correct
    }

    fun DoJumpEndAnimation() {
        animationTimer = 0.0
        status = Status.JUMPEND;
        jump = false
        currentAnimationIndex = 2 //jumpend animation
        animationMaxTimer = animator.getAnimationDuration(currentAnimationIndex)
        position.y = 0.0f //be sure to put the spider back on the ground
        vertspeed = 0.0f
    }

    fun ApplyMovement(dTime : Double, speed : Float) {
        if(status == Status.JUMPSTART || status == Status.JUMPING)
            vertspeed -= (gravity * dTime).toFloat()
        rotation += (rotationspeed * dTime).toFloat()
        position.x += (speed * cos(rotation) * dTime).toFloat()
        position.y += (vertspeed * dTime).toFloat()
        if(position.y<0) {
            position.y = 0.0f
        }
        position.z += (speed * sin(rotation) * dTime).toFloat()
    }
    private fun random(lower: Float, upper: Float) : Float {
        return ((Math.random() * (upper-lower))+lower ).toFloat()
    }
}
