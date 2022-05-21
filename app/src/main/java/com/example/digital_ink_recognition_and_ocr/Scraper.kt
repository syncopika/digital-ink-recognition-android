package com.example.digital_ink_recognition_and_ocr

// had trouble using skrape 1.2.1, e.g. java.lang.NoSuchFieldError: No field INSTANCE of type Lorg/apache/http/message/BasicLineFormatter
// maybe revisit sometime later

/*
import it.skrape.fetcher.skrape
import it.skrape.fetcher.HttpFetcher
import it.skrape.core.htmlDocument
import it.skrape.fetcher.extractIt
import it.skrape.selects.html5.span

data class Data(
    var pinyin: String = ""
)

class Scraper {
    fun extract(character: String){
        println("in scraper extract")
        val data = skrape(HttpFetcher) {
            request {
                // e.g. https://en.wiktionary.org/wiki/%E9%BE%8D
                url = "https://en.wiktionary.org/wiki/$character" // need to urlencode the character?
            }

            extractIt<Data> {
                htmlDocument {
                    it.pinyin = span {
                        withClass = ".form-of"
                        findFirst {
                            text
                        }
                    }
                }
            }
        }

        println(data)
    }
}*/

/*
fun main(args: Array<String>){
    val x = Scraper()
    print(x.extract("龍"))
}*/