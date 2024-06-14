The subprojects in tese directories depend on the jcyclone-core.jar being present in the core/build/dist directory. So do an 'ant jar' in the core/ directory first. 

Some of these subprojects depend on others, so you need to do an 'ant jar' on them in the following order:
adisk
asocket
http
atls
gnutella

You can use the handy compileAll.sh script to compile all of the subprojects at the same time. 
It just runs ant command in the order specified above, passing all the arguments to it. 
So if you wanted to run 'ant clean jar' on all of the projects, just run 'compileAll.sh clean jar'.
Or you can do 
compileAll.sh clean
compileAll.sh jar


