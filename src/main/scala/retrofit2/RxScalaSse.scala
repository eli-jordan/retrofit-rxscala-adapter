package retrofit2

import java.lang.annotation.Annotation
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import rx.lang.scala.Observable
import rx.lang.scala.Subscriber
import okhttp3.ResponseBody
import scala.io.Source
import rx.schedulers.Schedulers
import rx.lang.scala.Scheduler

object RxScalaSse {

    /**
     * This type encapsulates an sse message that has been received
     */
    case class MessageEvent[A](id: String, name: String, data: A)

    /**
     * The retrofit call adapter factory that generates a call adapter that can receive and parse server
     * sent events.
     */
    object CallAdapterFactory extends CallAdapter.Factory {

        override def get(returnType: Type, annotations: Array[Annotation], retrofit: Retrofit): CallAdapter[_] = {
            val rawType = Utils.getRawType(returnType)
            if (rawType.getCanonicalName == "rx.lang.scala.Observable") {
                val eventType = Utils.getParameterUpperBound(0, returnType.asInstanceOf[ParameterizedType])
                if (Utils.getRawType(eventType) == classOf[MessageEvent[_]]) {
                    val dataType = Utils.getParameterUpperBound(0, eventType.asInstanceOf[ParameterizedType])
                    new RxScalaSseCallAdapter(createBodyConverter(dataType, annotations, retrofit))
                } else {
                    null
                }

            } else {
                // this adapter doesn't support the type
                null
            }
        }
    }

    private class RxScalaSseCallAdapter[A](val bodyConverter: String => A) extends CallAdapter[Observable[_]] {

        /**
         * We hard code the underlying response type to ResponseBody so we can directly access the stream
         */
        override val responseType = classOf[ResponseBody]

        override def adapt[R](call: Call[R]): Observable[_] = {
            val callBody = call.asInstanceOf[Call[ResponseBody]]
            Observable(toObservable(callBody))
        }

        /**
         * This function is used to construct an observable. It executes the Call and notifies the subscriber
         * of the result.
         */
        private[this] def toObservable[R <: ResponseBody](call: Call[R])(subscriber: Subscriber[MessageEvent[_]]): Unit = schedule {

            // Attempt to cancel the call if it is still in-flight on unsubscription.
            subscriber.add(call.cancel())

            try {
                val response = call.execute()
                val source = Source.fromInputStream(response.body().byteStream())
                if (!subscriber.isUnsubscribed) {
                    notifyFrom(source, subscriber)
                }
            } catch {
                case t: Throwable => {
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onError(t)
                    }
                }
            }

            if (!subscriber.isUnsubscribed) {
                subscriber.onCompleted()
            }
        }

        /**
         * Streams lines from the response body recognising the appropriate SSE delimiters
         * and dispatches the events to the subscriber
         */
        def notifyFrom(body: Source, subscriber: Subscriber[MessageEvent[_]]): Unit = {
            val DataLine = "^data:(.*)".r
            val NameLine = "^event:(.*)".r
            val IdLine = "^id:(.*)".r
            val SeparatorLine = "\\W*".r

            val CommentLine = "^:.*".r

            var data = Vector[String]()
            var id = ""
            var name = ""

            body.getLines foreach {
                case DataLine(content) => data = data :+ content.trim()
                case NameLine(n)       => name = n.trim()
                case IdLine(i)         => id = i.trim()
                case SeparatorLine() => {
                    val event = MessageEvent(id, name, bodyConverter(data.mkString("\n")))
                    data = Vector()
                    id = ""
                    name = ""
                    subscriber.onNext(event)
                }
                case CommentLine() => println("comment")
            }
        }
    }

    def createBodyConverter(bodyType: Type, annotations: Array[Annotation], retrofit: Retrofit)(data: String): Any = {
        if (bodyType == classOf[String]) {
            data
        } else {
            retrofit.responseBodyConverter(bodyType, annotations).convert(ResponseBody.create(null, data))
        }
    }

    def schedule(work: => Unit): Unit = {
        import rx.lang.scala.JavaConversions._
        Schedulers.computation().createWorker.schedule(work)
    }

}