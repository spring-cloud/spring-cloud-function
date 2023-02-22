Classes in this package should ideally reside in spring-web somewhere as a light weight HTTP proxy, since they are independent of the 
context of the execution (i.e., AWS or Azure or whatever).
In fact classes in these package is a slimed-down copy of similar classes in MockMVC.
