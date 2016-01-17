package sample

import rx.lang.scala.Observable
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.Retrofit
import retrofit2.RxScalaSse

object SampleSse {

   trait HelloService {

      @Streaming
      @GET("/hello")
      def events(): Observable[RxScalaSse.MessageEvent[String]]
   }

   def main(args: Array[String]): Unit = {
      val retrofit = new Retrofit.Builder()
         .baseUrl("http://localhost:8096")
         .addCallAdapterFactory(retrofit2.RxScalaSse.CallAdapterFactory)
         .build();

      val service = retrofit.create(classOf[HelloService])
      val e1 = service.events().take(10)
      val e2 = service.events().take(10)
      
      e1.merge(e2).toBlocking.subscribe(println(_))
   }
   
}