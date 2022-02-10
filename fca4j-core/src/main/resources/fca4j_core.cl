__kernel void computeIntent(__global const int* matrix,__global const int* extent,__const int nbattr,__global int* result) 
	{
		    int numobj = get_global_id(0);
		    int numattr = get_global_id(1);
			result[numattr]=1;
			barrier(CLK_LOCAL_MEM_FENCE);			
		    int val=matrix[numattr+nbattr*numobj];
		    int ext=extent[numobj];
		    if(ext!=0 && val==0)
		    	result[numattr]=0;
	}       
__kernel void computeHasseDiagram(__global const int* matrix)
{
}	    
	