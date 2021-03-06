package me.dmdev.rxpm

import com.jakewharton.rxrelay2.*
import io.reactivex.*
import io.reactivex.disposables.*
import io.reactivex.functions.*
import me.dmdev.rxpm.navigation.*

/**
 * Parent class for any Presentation Model.
 */
abstract class PresentationModel {

    enum class Lifecycle {
        CREATED, BINDED, RESUMED, PAUSED, UNBINDED, DESTROYED
    }

    private val compositeDestroy = CompositeDisposable()
    private val compositeUnbind = CompositeDisposable()
    private val compositePause = CompositeDisposable()

    private val lifecycle = BehaviorRelay.create<Lifecycle>()
    private val unbind = BehaviorRelay.createDefault(true)
    internal val paused = BehaviorRelay.createDefault(true)

    /**
     * The [lifecycle][Lifecycle] of this presentation model.
     */
    val lifecycleObservable: Observable<Lifecycle> = lifecycle.distinctUntilChanged()
    internal val lifecycleConsumer = lifecycle.asConsumer()

    /**
     * Current state of this presentation model lifecycle.
     *
     * @return [lifecycle state][Lifecycle] or null if this presentation model is not created yet.
     */
    val currentLifecycleState: Lifecycle? get() = lifecycle.value

    init {
        lifecycleObservable
            .takeUntil { it == Lifecycle.DESTROYED }
            .subscribe {
                @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
                when (it) {
                    Lifecycle.CREATED -> {
                        onCreate()
                    }
                    Lifecycle.BINDED -> {
                        unbind.accept(false)
                        onBind()
                    }
                    Lifecycle.RESUMED -> {
                        paused.accept(false)
                        onResume()
                    }
                    Lifecycle.PAUSED -> {
                        paused.accept(true)
                        compositePause.clear()
                        onPause()
                    }
                    Lifecycle.UNBINDED -> {
                        unbind.accept(true)
                        compositeUnbind.clear()
                        onUnbind()
                    }
                    Lifecycle.DESTROYED -> {
                        compositeDestroy.clear()
                        onDestroy()
                    }
                }
            }
            .untilDestroy()
    }

    /**
     * Called when the presentation model is created.
     * @see [onDestroy]
     */
    protected open fun onCreate() {}

    /**
     * Called when the presentation model binds to the [view][PmView].
     * @see [onUnbind]
     */
    protected open fun onBind() {}

    /**
     * Called when the presentation model is resumed.
     * @see [onPause]
     */
    protected open fun onResume() {}

    /**
     * Called when the presentation model is paused.
     * @see [onResume]
     */
    protected open fun onPause() {}

    /**
     * Called when the presentation model unbinds from the [view][PmView].
     * @see [onBind]
     */
    protected open fun onUnbind() {}

    /**
     * Called just before the presentation model will be destroyed.
     * @see [onCreate]
     */
    protected open fun onDestroy() {}

    /**
     * Attaches `this` (child presentation model) to the [parent] presentation model.
     * This presentation model will be bind to the lifecycle of the [parent] presentation model.
     *
     * @see [detachFromParent]
     */
    fun attachToParent(parent: PresentationModel) {

        require(parent != this) { "Presentation model can't be attached to itself." }

        check(!lifecycle.hasValue()) { "Presentation model can't be a child more than once. It must not be reused." }

        when (parent.lifecycle.value) {

            Lifecycle.RESUMED -> {
                parent.lifecycleObservable
                    .startWithArray(Lifecycle.CREATED, Lifecycle.BINDED)
                    .subscribe(lifecycleConsumer)
            }

            Lifecycle.PAUSED -> {
                parent.lifecycleObservable
                    .skip(1)
                    .startWithArray(Lifecycle.CREATED, Lifecycle.BINDED)
                    .subscribe(lifecycleConsumer)
            }

            Lifecycle.BINDED -> {
                parent.lifecycleObservable
                    .startWith(Lifecycle.CREATED)
                    .subscribe(lifecycleConsumer)
            }

            Lifecycle.UNBINDED -> {
                parent.lifecycleObservable
                    .skip(1)
                    .startWith(Lifecycle.CREATED)
                    .subscribe(lifecycleConsumer)
            }

            null,
            Lifecycle.CREATED -> {
                parent.lifecycleObservable
                    .subscribe(lifecycleConsumer)
            }

            Lifecycle.DESTROYED -> {
                throw IllegalStateException("Presentation model can't be attached as a child to the already destroyed parent.")
            }

        }.untilDestroy()

        if (this is NavigationalPm && parent is NavigationalPm) {
            navigationMessages.observable
                .subscribe(parent.navigationMessages.consumer)
                .untilDestroy()
        }
    }

    /**
     * Detaches this presentation model from parent.
     * @see [attachToParent]
     */
    fun detachFromParent() {

        when (lifecycle.value) {

            Lifecycle.CREATED -> {
                lifecycleConsumer.accept(Lifecycle.DESTROYED)
            }

            Lifecycle.BINDED -> {
                lifecycleConsumer.accept(Lifecycle.UNBINDED)
                lifecycleConsumer.accept(Lifecycle.DESTROYED)
            }

            Lifecycle.RESUMED -> {
                lifecycleConsumer.accept(Lifecycle.PAUSED)
                lifecycleConsumer.accept(Lifecycle.UNBINDED)
                lifecycleConsumer.accept(Lifecycle.DESTROYED)
            }

            Lifecycle.PAUSED -> {
                lifecycleConsumer.accept(Lifecycle.UNBINDED)
                lifecycleConsumer.accept(Lifecycle.DESTROYED)
            }

            Lifecycle.UNBINDED -> {
                lifecycleConsumer.accept(Lifecycle.DESTROYED)
            }

            null,
            Lifecycle.DESTROYED -> {
                //  do nothing
            }
        }
    }

    /**
     * Local extension to add this [Disposable] to the [CompositeDisposable][compositePause]
     * that will be CLEARED ON [PAUSED][Lifecycle.PAUSED].
     */
    fun Disposable.untilPause() {
        compositePause.add(this)
    }

    /**
     * Local extension to add this [Disposable] to the [CompositeDisposable][compositeUnbind]
     * that will be CLEARED ON [UNBIND][Lifecycle.UNBINDED].
     */
    fun Disposable.untilUnbind() {
        compositeUnbind.add(this)
    }

    /**
     * Local extension to add this [Disposable] to the [CompositeDisposable][compositeDestroy]
     * that will be CLEARED ON [DESTROY][Lifecycle.DESTROYED].
     */
    fun Disposable.untilDestroy() {
        compositeDestroy.add(this)
    }

    /**
     * Consumer of the [State].
     * Accessible only from a [PresentationModel].
     *
     * Use to subscribe the state to some [Observable] source.
     */
    protected val <T> State<T>.consumer: Consumer<T> get() = relay

    /**
     * Observable of the [Action].
     * Accessible only from a [PresentationModel].
     *
     * Use to subscribe to this [Action]s source.
     */
    protected val <T> Action<T>.observable: Observable<T> get() = relay

    /**
     * Consumer of the [Command].
     * Accessible only from a [PresentationModel].
     *
     * Use to subscribe the command to some [Observable] source.
     */
    protected val <T> Command<T>.consumer: Consumer<T> get() = relay

}

