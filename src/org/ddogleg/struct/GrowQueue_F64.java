/*
 * Copyright (c) 2012-2018, Peter Abeles. All Rights Reserved.
 *
 * This file is part of DDogleg (http://ddogleg.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ddogleg.struct;


import java.util.Arrays;

/**
 * This is a queue that is composed of integers.  Elements are added and removed from the tail
 *
 * @author Peter Abeles
 */
public class GrowQueue_F64 implements GrowQueue<GrowQueue_F64>{

	public double data[];
	public int size;

	public GrowQueue_F64( int maxSize ) {
		data = new double[ maxSize ];
		this.size = 0;
	}

	public GrowQueue_F64() {
		this(10);
	}

	/**
	 * Creates a queue with the specified length as its size filled with all zeros
	 */
	public static GrowQueue_F64 zeros( int length ) {
		GrowQueue_F64 out = new GrowQueue_F64(length);
		out.size = length;
		return out;
	}

	public static GrowQueue_F64 array( double ...values ) {
		GrowQueue_F64 out = zeros(values.length);
		for (int i = 0; i < values.length; i++) {
			out.data[i] = values[i];
		}
		return out;
	}

	@Override
	public void reset() {
		size = 0;
	}

	public void addAll( GrowQueue_F64 queue ) {
		if( size+queue.size > data.length ) {
			double temp[] = new double[ (size+queue.size) * 2];
			System.arraycopy(data,0,temp,0,size);
			data = temp;
		}
		System.arraycopy(queue.data,0,data,size,queue.size);
		size += queue.size;
	}

	public void addAll( double[] array , int startIndex , int endIndex ) {
		if( endIndex > array.length )
			throw new IllegalAccessError("endIndex is larger than input array");

		int arraySize = endIndex-startIndex;

		if( size+arraySize > data.length ) {
			double temp[] = new double[ (size+arraySize) * 2];
			System.arraycopy(data,0,temp,0,size);
			data = temp;
		}
		System.arraycopy(array,startIndex,data,size,arraySize);
		size += arraySize;
	}

	public void add( double val ) {
		push(val);
	}

	public void push( double val ) {
		if( size == data.length ) {
			double temp[];
			try {
				temp = new double[ size * 2];
			} catch( OutOfMemoryError e ) {
				System.gc();
//				System.out.println("Memory on size "+size+" or "+(size*8/1024/1024)+" MB");
//				System.out.println("Trying smaller increment");
				temp = new double[ 3*size/2];
			}
			System.arraycopy(data,0,temp,0,size);
			data = temp;
		}
		data[size++] = val;
	}

	public void remove( int index ) {
		for( int i = index+1; i < size; i++ ) {
			data[i-1] = data[i];
		}
		size--;
	}


	/**
	 * Removes elements from the list starting at 'first' and ending at 'last'
	 * @param first First index you wish to remove. Inclusive.
	 * @param last Last index you wish to remove. Inclusive.
	 */
	public void remove( int first , int last ) {
		if( last < first )
			throw new IllegalArgumentException("first <= last");
		if( last >= size )
			throw new IllegalArgumentException("last must be less than the max size");

		int delta = last-first+1;
		for( int i = last+1; i < size; i++ ) {
			data[i-delta] = data[i];
		}
		size -= delta;
	}

	/**
	 * Inserts the value at the specified index and shifts all the other values down.
	 */
	public void insert( int index , double value ) {
		if( size == data.length ) {
			double temp[] = new double[ size * 2];
			System.arraycopy(data,0,temp,0,index);
			temp[index] = value;
			System.arraycopy(data,index,temp,index+1,size-index);
			this.data = temp;
			size++;
		} else {
			size++;
			for( int i = size-1; i > index; i-- ) {
				data[i] = data[i-1];
			}
			data[index] = value;
		}
	}

	public double removeTail() {
		if( size > 0 ) {
			size--;
			return data[size];
		} else {
			throw new RuntimeException("Size zero, no tail");
		}
	}

	public double get( int index ) {
		if( index < 0 || index >= size)
			throw new IndexOutOfBoundsException("index = "+index+"  size = "+size);
		return data[index];
	}

	/**
	 * Gets the value at the index which corresponds to the specified fraction
	 * @param fraction 0 to 1 inclusive
	 * @return value at fraction
	 */
	public double getFraction( double fraction ) {
		return get( (int)((size-1)*fraction) );
	}

	public double unsafe_get( int index ) {
		return data[index];
	}

	public void set( int index , double value ) {
		data[index] = value;
	}

	@Override
	public void setTo( GrowQueue_F64 original ) {
		resize(original.size);
		System.arraycopy(original.data, 0, data, 0, size());
	}

	@Override
	public void resize( int size ) {
		if( data.length < size ) {
			data = new double[size];
		}
		this.size = size;
	}

	@Override
	public void extend( int size ) {
		if( data.length < size ) {
			double []tmp = new double[size];
			System.arraycopy(data,0,tmp,0,this.size);
			data = tmp;
		}
		this.size = size;
	}

	public void fill( double value ) {
		Arrays.fill(data, 0, size, value);
	}

	public boolean contains( double value ) {
		for (int i = 0; i < size; i++) {
			if( data[i] == value )
				return true;
		}
		return false;
	}

	@Override
	public void setMaxSize( int size ) {
		if( data.length < size ) {
			data = new double[size];
		}
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public void zero() {
		Arrays.fill(data,0,size,0);
	}

	@Override
	public GrowQueue_F64 copy() {
		GrowQueue_F64 ret = new GrowQueue_F64(size);
		ret.setTo(this);
		return ret;
	}

	@Override
	public void flip() {
		if( size <= 1 )
			return;

		int D = size/2;
		for (int i = 0,j=size-1; i < D; i++,j--) {
			double tmp = data[i];
			data[i] = data[j];
			data[j] = tmp;
		}
	}

	public double pop() {
        return data[--size];
    }

	/**
	 * Returns the index of the first element with the specified 'value'.  return -1 if it wasn't found
	 * @param value Value to search for
	 * @return index or -1 if it's not in the list
	 */
	public int indexOf( double value ) {
		for (int i = 0; i < size; i++) {
			if( data[i] == value )
				return i;
		}
		return -1;
	}

	public int indexOfGreatest() {
		if( size <=- 0 )
			return -1;

		int selected = 0;
		double best = data[0];

		for (int i = 1; i < size; i++) {
			if( data[i] > best ) {
				best = data[i];
				selected = i;
			}
		}

		return selected	;
	}

	public int indexOfLeast() {
		if( size <=- 0 )
			return -1;

		int selected = 0;
		double best = data[0];

		for (int i = 1; i < size; i++) {
			if( data[i] < best ) {
				best = data[i];
				selected = i;
			}
		}

		return selected;
	}

	@Override
	public void sort() {
		Arrays.sort(data,0,size);
	}
}
