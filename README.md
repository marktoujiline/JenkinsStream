# JenkinsStream

Purpose: provide a flexible, automated, declarative approach to bringing up a neptune environment.

## Motivation
The flow to set up an environment for testing is currently:
1. Create or find a tenant
2. Deploy necessary stacks
3. Setup components being tested
4. Create user/plan/etc. either manually or with automated tests

### Create or find a tenant
Tenants are E.X.P.E.N.S.I.V.E. Due to the time investment needed to setup a tenant, most teams elect to have long lived tenants.
This means that a tenant may be running for a full 2 weeks. The intended approach is for devs to only have a
tenant spun up when they need one. Until the effort and time to setup a tenant is reduced, this will always be
a huge waste of money. If a tenant exist only during work hours (8-8 est), the tenancy cost would be cut in half.

There is another issue: stale environments. Setting up the tenant as infrequently as 2 weeks to a month means
you are not testing your changes against the most up-to-date stack. Deploying daily gives you the opportunity to
update your dependencies and discover issues sooner rather than later.

### Deploy snapshots of altered components
In most use cases, this step is quite painless. However, some testing setups
require more than just component versions to be deployed. Sometimes, environment
variables need to be changed between different test runs. This can be done relatively
quickly by hand, but blocks the possibility of a CI build.

### Create user/plan/etc. either manually or with automated tests
This step is similar to the previous one in that it is only problematic under rare circumstances.
An automation test will certainly set up anything you need, BUT kicking off the test is still a manual
process that can impede a scheduled tenant setup.

## Vision

There are three components necessary to solving the above issues:
* Triggering Jenkins jobs AND use the job status
* Checking and updating tenant configurations (Marathon)
* Running automated tests

Problem 1 can be solved by simply triggering the CreateTenant job.

Problem 2 can be solved by deploying snapshots via Jenkins and updating the environment variables afterwards
with the Marathon APi.

Problem 3 can be solved one of two ways: include the python source code as a dependency and run it
directly OR abstract the test to a Jenkins job and just trigger the job.

All of the above can be wrapped in a chron job that runs daily. Every morning, you will have an up-to-date
functional environment that you can rely on and is cheaper.

Motto: Anything you need to do manually and repeatedly, should be done automatically.

## Implementation

So what does this look like in practice? You can take a look at the `scripts` package. But essentially,
the code will end up being a large for-comprehension. It is trivial to encode build steps:
```
for {
    createTenant <- jenkinsClient.createTenant("engage", "3 hours", "dev")
    tenant = createTenant.description
    engageStack <- jenkinsClient.deployStack(tenant, "engage")
    engineStack <- jenkinsClient.deployStack(tenant, "engine")
  } yield ???

``` 

#### Error Handling
What happens if a job finishes, but fails? Well, that's entirely up to you.

In the library, there are some error handlers already defined for you. They are:
* stopOnNonSuccessfulBuild
* continueOnNonSuccessfulBuild

You can pass a handler to the job being run. That determines how you want to handle a non-successful build. 

```
implicit val continueOnNonSuccessfulBuild
for {
    createTenant <- jenkinsClient.createTenant("engage", "3 hours", "dev")(stopOnNonSuccessfulBuild)
    tenant = createTenant.description
    engageStack <- jenkinsClient.deployStack(tenant, "engage")
    engineStack <- jenkinsClient.deployStack(tenant, "engine")
  } yield ???

```

The above code will create a tenant, and if the tenant is create successfully, the rest of the stacks will be deployed. But if the engage stack fails to finish, the engine stack job will still be triggered.

You can have any number of conditions as long as the condition can be determined from the BuildInfo

## Future
This project is still very much in beta. Some work that still needs to be done:
* Unit testing
* Fixing the implicit error handlers and how they are passed along
* Kicking off automated tests
