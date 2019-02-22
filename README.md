# randnum-svc

## Requirements

- [Apache Maven 3.x](http://maven.apache.org)

## Preparing

```
cd $PROJECT_ROOT
mvn clean install
```

## Running the example standalone

```
cd $PROJECT_ROOT
mvn spring-boot:run
```

## Running the example in OpenShift

```
oc new-project demo
oc create -f src/main/kube/serviceaccount.yml
oc create -f src/main/kube/configmap.yml
oc create -f src/main/kube/secret.yml
oc secrets add sa/randnum-svc-sa secret/randnum-svc-secret
oc policy add-role-to-user view system:serviceaccount:demo:randnum-svc-sa
mvn -P openshift clean install fabric8:deploy
```

## Testing the code

To upload bulk FLR data you can use `curl` (as seen below), or you can use the upload form at 'http://localhost:8080/upload'.

```
curl -X POST -F '@file=@./src/test/data/5 cdp_rand_num_02012019' 'http://localhost:8080/camel/randnum/upload/'
```

To get all randNums:

```
curl -X GET -H 'Accept: application/json' 'http://localhost:8080/camel/randnum'
```

To get a randNum by accountNum:

```
curl -X GET -H 'Accept: application/json' 'http://localhost:8080/camel/randnum/296662709'
```
