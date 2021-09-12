package com.marcherdiego.events.navigator

import com.intellij.openapi.util.Condition
import java.lang.Runnable
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

class PingEDT(private val myShutUpCondition: Condition<*>, //-1 means indefinite
              private val myMaxUnitOfWorkThresholdMs: Int,
              private val pingAction: Runnable) {

    @Volatile
    private var stopped = false

    @Volatile
    private var pinged = false
    private val invokeLaterScheduled = AtomicBoolean()
    private val myUpdateRunnable = Runnable {
        val b = invokeLaterScheduled.compareAndSet(true, false)
        assert(b)
        if (stopped || myShutUpCondition.value(null)) {
            stop()
            return@Runnable
        }
        val start = System.currentTimeMillis()
        while (true) {
            if (!processNext()) {
                break
            }
            val finish = System.currentTimeMillis()
            if (myMaxUnitOfWorkThresholdMs != -1 && finish - start > myMaxUnitOfWorkThresholdMs) break
        }
        if (isEmpty.not()) {
            scheduleUpdate()
        }
    }

    private val isEmpty: Boolean
        get() = pinged.not()

    private fun processNext(): Boolean {
        pinged = false
        pingAction.run()
        return pinged
    }

    // returns true if invokeLater was called
    fun ping(): Boolean {
        pinged = true
        return scheduleUpdate()
    }

    // returns true if invokeLater was called
    private fun scheduleUpdate(): Boolean {
        if (stopped.not() && invokeLaterScheduled.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(myUpdateRunnable)
            return true
        }
        return false
    }

    private fun stop() {
        stopped = true
    }
}
