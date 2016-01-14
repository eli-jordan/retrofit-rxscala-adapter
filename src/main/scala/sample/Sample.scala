package sample

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import retrofit2.JacksonConverterFactory
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import rx.lang.scala.Observable
import com.fasterxml.jackson.databind.DeserializationFeature

object Playing {

  // convention is to use lower case for annotations in scala
  type get = retrofit2.http.GET
  type path = retrofit2.http.Path
  
  // define out domain object
  case class Contributor(login: String, contributions: Int)

  // define our service
  trait GithubService {

    @get("/repos/{owner}/{repo}/contributors")
    def contributors(@path("owner") owner: String,
                     @path("repo")   repo: String): Observable[List[Contributor]]
  }


  def main(args: Array[String]): Unit = {
    
    // use the jackson coverter with the scala module registered
    // so that jackson knows how to handle the standard scala types
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    
    val retrofit = new Retrofit.Builder()
      .baseUrl("https://api.github.com")
      .addCallAdapterFactory(retrofit2.RxScalaCallAdapterFactory)
      .addConverterFactory(JacksonConverterFactory.create(mapper))
      .build();
    
    val github = retrofit.create(classOf[GithubService])

    val result = for {
      contributors <- github.contributors("square", "retrofit")
      c <- Observable.from(contributors)
    } yield c

    result.foreach(println)
  }
}