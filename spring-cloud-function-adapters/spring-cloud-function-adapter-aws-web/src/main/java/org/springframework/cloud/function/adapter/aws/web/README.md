Classes in this package would remain specific to AWS (in this case). There would be something similar in Azure and others.

And these classes would depend on what is currently in `org.springframework.web.client` package of this module. 
However, ideally the contents of the `org.springframework.web.client` package should reside in spring-web somewhere as a light weight 
HTTP proxy as we technically already have it in a form of MockMVC.