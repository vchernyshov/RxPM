package me.dmdev.rxpm.test

import me.dmdev.rxpm.*
import me.dmdev.rxpm.PresentationModel.Lifecycle.*

/**
 * Helps to test [PresentationModel].
 *
 * @param pm presentation model under test.
 */
class PmTestHelper(val pm: PresentationModel) {

    enum class LifecycleSteps { ALL, BYPASS_BINDING, BYPASS_RESUMING }

    /**
     * Sets the lifecycle of the [presentation model][pm] under test to the specified [state][lifecycleState].
     * This will also create natural sequence of states before the requested one.
     *
     * **Note** that because of it's nature [Command][Command] emits items right away
     * only in [RESUMED] lifecycle state. So if you want to test it, be sure to set the state.
     *
     * @param lifecycleState lifecycle state to set to.
     * @param lifecycleSteps lifecycle path.
     * Sometimes when testing you may need a shorter lifecycle path: bypassing [resuming][LifecycleSteps.BYPASS_BINDING] or [binding][LifecycleSteps.BYPASS_RESUMING].
     * By default it is [all steps][LifecycleSteps.ALL]
     *
     * @throws IllegalStateException if requested state is not acceptable considering the current state.
     */
    fun setLifecycleTo(
        lifecycleState: PresentationModel.Lifecycle,
        lifecycleSteps: LifecycleSteps = LifecycleSteps.ALL
    ) {

        checkStateAllowed(lifecycleState)

        when {
            isResumedAgain(lifecycleState) -> pm.lifecycleConsumer.accept(RESUMED)
            isBindedAgain(lifecycleState) -> pm.lifecycleConsumer.accept(BINDED)
            else -> {

                val currentLifecycleState = pm.currentLifecycleState

                PresentationModel.Lifecycle.values()
                    .filter {
                        if (currentLifecycleState != null) {
                            it > currentLifecycleState
                        } else {
                            true
                        }
                    }
                    .filter { it <= lifecycleState }
                    .filter {
                        when (lifecycleSteps) {
                            LifecycleSteps.BYPASS_RESUMING -> {
                                if (lifecycleState > PAUSED) {
                                    it < RESUMED || it > PAUSED
                                } else {
                                    true
                                }
                            }
                            LifecycleSteps.BYPASS_BINDING -> {
                                if (lifecycleState > UNBINDED) {
                                    it < BINDED || it > UNBINDED
                                } else {
                                    true
                                }
                            }
                            LifecycleSteps.ALL -> true
                        }
                    }
                    .forEach {
                        pm.lifecycleConsumer.accept(it)
                    }
            }
        }
    }

    private fun checkStateAllowed(lifecycleState: PresentationModel.Lifecycle) {
        pm.currentLifecycleState?.let { currentState ->
            check(!(lifecycleState <= currentState
            && !isBindedAgain(lifecycleState)
            && !isResumedAgain((lifecycleState)))
            ) { "You can't set lifecycle state as $lifecycleState when it already is $pm.currentLifecycleState." }
        }
    }

    private fun isResumedAgain(lifecycleState: PresentationModel.Lifecycle): Boolean {
        return pm.currentLifecycleState == PAUSED && lifecycleState == RESUMED
    }

    private fun isBindedAgain(lifecycleState: PresentationModel.Lifecycle): Boolean {
        return pm.currentLifecycleState == UNBINDED && lifecycleState == BINDED
    }
}