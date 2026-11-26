import { describe, expect, it } from 'vitest'
import { calculatePlates, calculateTotalWeight, plateColor } from './plateCalculator'

describe('plate loading', () => {
  it('uses an exact non-greedy solution with the fewest plates', () => {
    expect(calculatePlates(12, [5,3,1],0,2)).toEqual({totalWeight:12,platesPerSide:[5,1],isExact:true})
    expect(calculatePlates(12, [4,3,1],0,2).platesPerSide).toEqual([3,3])
    expect(calculatePlates(225,[45,25,10,5,2.5])).toMatchObject({totalWeight:225,platesPerSide:[45,45]})
  })
  it('prefers an underload on equal error, then favors large plates on equal coin counts', () => {
    expect(calculatePlates(73, [10],45,2).totalWeight).toBe(65)
    expect(calculatePlates(75,[10],45,2).totalWeight).toBe(65)
    expect(calculatePlates(12,[5,3,1],0,2,{'5':2,'3':4,'1':2}).platesPerSide).toEqual([5,1])
  })
  it('respects physical counts, odd stock, single-sided equipment and normalized aliases', () => {
    expect(calculatePlates(225,[45,25],45,2,{'45':3,'25':1})).toMatchObject({totalWeight:135,platesPerSide:[45]})
    expect(calculatePlates(70,[25],45,1,{'25':1}).isExact).toBe(true)
    expect(calculatePlates(135,[45],45,2,{'45':2,'45.001':1}).totalWeight).toBe(45)
    expect(calculatePlates(16,[2,10],0,2,{'2':2,'10':2}).totalWeight).toBe(20)
  })
  it('handles fractional denominations, unachievable targets and invalid equipment', () => {
    expect(calculatePlates(5,[1.25],0,2).platesPerSide).toEqual([1.25,1.25])
    expect(calculatePlates(20,[0,-1,NaN],45,2)).toEqual({totalWeight:45,isExact:false,platesPerSide:[]})
    expect(calculateTotalWeight([45,2.5],45,2)).toBe(140)
    expect(plateColor(55)).toBe('#E74C3C')
    expect(plateColor(45)).toBe('#3498DB')
  })
  it('matches exhaustive bounded enumeration for every target in a small mixed inventory', () => {
    const choices=[7,5,3], inventory={'7':4,'5':6,'3':2}
    const loads:number[][]=[]
    for(let a=0;a<=2;a++) for(let b=0;b<=3;b++) for(let c=0;c<=1;c++){
      loads.push([...Array<number>(a).fill(7),...Array<number>(b).fill(5),...Array<number>(c).fill(3)])
    }
    for(let goal=0;goal<=70;goal++){
      const ordered=[...loads].sort((a,b)=>{
        const x=calculateTotalWeight(a,0,2),y=calculateTotalWeight(b,0,2)
        return Math.abs(goal-x)-Math.abs(goal-y) || x-y || a.length-b.length || b.filter(w=>w===7).length-a.filter(w=>w===7).length
      })
      expect(calculatePlates(goal,choices,0,2,inventory).platesPerSide).toEqual(ordered[0])
    }
  })
})
