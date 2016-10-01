Register a Function:

```
./register.sh -n uppercase -f "f->f.map(s->s.toString().toUpperCase())"
```

Run a Stream Processing Microservice using that Function:

```
./stream.sh -i words -o uppercaseWords -f uppercase
```

Run a REST Microservice using that Function:

```
./web.sh -p /words -f uppercase
```

(more docs soon)
