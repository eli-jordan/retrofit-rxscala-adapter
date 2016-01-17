package retrofit2

import java.lang.annotation.Annotation
import java.lang.reflect.Type
import rx.lang.scala.Observable
import rx.lang.scala.Subscriber
import java.lang.reflect.ParameterizedType

object RxScala {

    object CallAdapterFactory extends CallAdapter.Factory {
        override def get(returnType: Type, annotations: Array[Annotation], retrofit: Retrofit): CallAdapter[_] = {
            val rawType = Utils.getRawType(returnType)
            if (rawType.getCanonicalName == "rx.lang.scala.Observable") {
                val responseType = Utils.getParameterUpperBound(0, returnType.asInstanceOf[ParameterizedType])
                new RxScalaCallAdapter(responseType)
            } else {
                // this adapter doesn't support the type
                null
            }
        }
    }

    private class RxScalaCallAdapter[A](override val responseType: Type) extends CallAdapter[Observable[_]] {
        override def adapt[R](call: Call[R]): Observable[_] = {
            Observable(toObservable(call)) flatMap {
                case response if response.isSuccess() => Observable.just(response.body())
                case response                         => Observable.error(new HttpException(response))
            }
        }
    }

    /**
     * This function is used to construct an observable. It executes the Call and notifies the subscriber
     * of the result.
     */
    private[this] def toObservable[A](call: Call[A])(subscriber: Subscriber[Response[A]]): Unit = {

        // Attempt to cancel the call if it is still in-flight on unsubscription.
        subscriber.add(call.cancel());

        try {
            val response = call.execute();
            if (!subscriber.isUnsubscribed) {
                subscriber.onNext(response);
            }
        } catch {
            case t: Throwable => {
                if (!subscriber.isUnsubscribed) {
                    subscriber.onError(t);
                }
            }
        }

        if (!subscriber.isUnsubscribed) {
            subscriber.onCompleted();
        }
    }
}