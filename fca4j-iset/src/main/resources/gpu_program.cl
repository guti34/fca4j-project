__kernel void and(__global int *a,__global const int *c)
		{    
			int gid = get_global_id(0);
			a[gid] = a[gid] & c[gid];
		}
__kernel void or(__global int *a, __global const int *c)
		{
		    int gid = get_global_id(0);
		    a[gid] = a[gid] | c[gid];
		 }
__kernel void nand(__global int *a,__global const int *c)
		{
		    int gid = get_global_id(0);
		    a[gid] = a[gid] & ~c[gid];
		}
__kernel void or3(__global const int *a,__global const int *b,__global int *c)
        {
            int gid = get_global_id(0);
            c[gid] = a[gid] | b[gid];
        }
__kernel void nand3(__global const int *a,__global const int *b,__global int *c)
        {
            int gid = get_global_id(0);
            c[gid] = a[gid] & ~b[gid];
        }
__kernel void and3(__global const int *a,__global const int *b,__global int *c)
        {
            int gid = get_global_id(0);
            c[gid] = a[gid] & b[gid];
        } 
__kernel void fill(__global int *a,__const int length)
		{    
			int gid = get_global_id(0);
			if(gid<length) a[gid] = 1;
		}
__kernel void clear(__global int *a,__const int length)
		{    
			int gid = get_global_id(0);
			if(gid<length) a[gid] = 0;
		}
__kernel void contains(__global const int* a,__global const int* b,__local int* scratch,__const int length,__global int* result) 
	{
	    int globalIndex = get_global_id(0);
	    int accumulator = 0;
	
	    // Loop sequentially over chunks of input vector
	    while (globalIndex < length) 
	    {
	        int elementA = a[globalIndex];
	        int elementB = b[globalIndex];
	        if(elementB==1 && elementA==0)
	        	accumulator ++;
	        globalIndex += get_global_size(0);
	    }
	
	    // Perform parallel reduction
	    int lid = get_local_id(0);
	    scratch[lid] = accumulator;
	    barrier(CLK_LOCAL_MEM_FENCE);
	    for(int offset = get_local_size(0) / 2; offset > 0; offset = offset / 2) 
	    {
	        if (lid < offset) 
	        {
	            int other = scratch[lid + offset];
	            int mine = scratch[lid];
	            scratch[lid] = mine + other;
	        }
	        barrier(CLK_LOCAL_MEM_FENCE);
	    }
	    if (lid == 0) 
	    {
	        result[get_group_id(0)] = scratch[0];
	    }
	}           
__kernel void equals(__global const int* a,__global const int* b,__local int* scratch,__const int length,__global int* result) 
	{
	    int globalIndex = get_global_id(0);
	    int accumulator = 0;
	
	    // Loop sequentially over chunks of input vector
	    while (globalIndex < length) 
	    {
	        int elementA = a[globalIndex];
	        int elementB = b[globalIndex];
	        if(elementB!= elementA)
	        	accumulator ++;
	        globalIndex += get_global_size(0);
	    }
	
	    // Perform parallel reduction
	    int lid = get_local_id(0);
	    scratch[lid] = accumulator;
	    barrier(CLK_LOCAL_MEM_FENCE);
	    for(int offset = get_local_size(0) / 2; offset > 0; offset = offset / 2) 
	    {
	        if (lid < offset) 
	        {
	            int other = scratch[lid + offset];
	            int mine = scratch[lid];
	            scratch[lid] = mine + other;
	        }
	        barrier(CLK_LOCAL_MEM_FENCE);
	    }
	    if (lid == 0) 
	    {
	        result[get_group_id(0)] = scratch[0];
	    }
	}           
__kernel void cardinality(__global const int* buffer,__local int* scratch,__const int length,__global int* result) 
	{
	    int globalIndex = get_global_id(0);
	    int accumulator = 0;
	
	    // Loop sequentially over chunks of input vector
	    while (globalIndex < length) 
	    {
	        int element = buffer[globalIndex];
	        accumulator += element;
	        globalIndex += get_global_size(0);
	    }
	
	    // Perform parallel reduction
	    int lid = get_local_id(0);
	    scratch[lid] = accumulator;
	    barrier(CLK_LOCAL_MEM_FENCE);
	    for(int offset = get_local_size(0) / 2; offset > 0; offset = offset / 2) 
	    {
	        if (lid < offset) 
	        {
	            int other = scratch[lid + offset];
	            int mine = scratch[lid];
	            scratch[lid] = mine + other;
	        }
	        barrier(CLK_LOCAL_MEM_FENCE);
	    }
	    if (lid == 0) 
	    {
	        result[get_group_id(0)] = scratch[0];
	    }
	}
__kernel void computeIntent(__global const int* matrix,__global const int* extent,__const int nbattr,__global int* result) 
	{
		    int numobj = get_global_id(0);
		    int numattr = get_global_id(1);
		    int val=matrix[numattr+nbattr*numobj];
		    int ext=extent[numobj];
		    if(ext!=0 && val==0)
		    result[numattr]=0;
	}           
__kernel void loseFrequency(__global const int* matrix,__global const int* extent,__const int nbattr,__const int effectiveTeta,__global int* result) 
	{
		    int numobj = get_global_id(0);
		    int numattr = get_global_id(1);
		    int val=matrix[numattr+nbattr*numobj];
		    int ext=extent[numobj];
		    if(ext!=0 && val==0)
		    result[numattr]=0;
	}           
	