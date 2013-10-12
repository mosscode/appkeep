
AppKeep
=================

Needs:
  High-performance, high-availability, near-mission-critical network service for storing/retrieving 
  application components (modules) in a secure fashion.
  
  Needs to be a suitable replacement for the constellation software-repository, the 'deployer' repository (e.g. download.foo.com).
  Needs to be suitable as the 'core' of the appsnap service; appsnap will rely on this for storage/download of apps.
  
  Need tools for pushing an app from the lab into an appkeep.
  
  The performance requirements are really throughput requirements: It needs to be able to handle massive amounts of 
  simultaneous downloads; this probably means it needs to be distributed.

  
  Binary diff download options?  This would be cool, and might be a really good place to do it.
  
SECURITY
  The most important security concern is authorization: we want to be able to restrict access to modules.
  A secondary concern is wire-security: it would be good to be able to keep people from being able to easily
    sniff the module off the wire.  However, this concern is far secondary to the speed (throughput and authorization
    concerns: compromises in wire security for the sake of throughput and authorization are acceptable.
  
PRESUPPOSITIONS:
  Applications are broken into modules
  The default way to download an app is to download all the modules and then put them together.
  Optionally, it would make sense for binary diff download of modules.

MODULE IDENTIFICATION:
  Need to support multiple ways of identifying modules.
     - by checksum (this is how the 'deployer' project does it)
     - by maven handle (e.g. groupId:artifactId:version)
     - OSGI???
     - yet-to-be-finalized Java7 modules????

FUNCTIONS:

    store a module
    load a module
       - binary diff algorithms included
    grant access to a module
      - some sort of authorization delegation mechanism
      
PARTS:
     Server
     Reusable swing components (for browsing the repo, displaying metadata, and uploading/downloading components)
     A tool for uploading an app from a maven repo (e.g. a maven repo URL and an app-spec)
        -CLI???
