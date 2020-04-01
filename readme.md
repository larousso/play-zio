# ZIO / ZLayer with playframework

## Start the app 

```
sbt '~run'
```

## Use the app 

```bash
curl -XPOST http://localhost:9000/users -H 'Content-Type:application/json' -d '{"name":"test"}'
```

```bash
curl http://localhost:9000/users
```

```bash
curl -XPUT http://localhost:9000/users/da8b2cd6-a88c-4a54-81b8-eeb7ce9075c5 -H 'Content-Type:application/json' -d '{"id":"da8b2cd6-a88c-4a54-81b8-eeb7ce9075c5","name":"test2"}'
```