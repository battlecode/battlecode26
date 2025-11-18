import { Button } from '../../button'
import { SymmetricMapEditorBrush } from './MapEditorBrush'
import React from 'react'
import { WallsBrush } from '../../../playback/Brushes'
import Round from '../../../playback/Round'
import { StaticMap } from '../../../playback/Map'
import { GameRenderer } from '../../../playback/GameRenderer'

async function getPixelDataFromDataURL(dataUrl: string, targetWidth: number, targetHeight: number): Promise<Uint8ClampedArray> {
    return new Promise((resolve, reject) => {
        const img = new Image()
        img.crossOrigin = 'anonymous' // avoids some browser restrictions

        img.onload = () => {
            const canvas = document.createElement('canvas')
            canvas.width = targetWidth
            canvas.height = targetHeight

            const ctx = canvas.getContext('2d')
            if (!ctx) return reject('Could not get 2D context')

            // Clear canvas with white background
            ctx.fillStyle = 'white'
            ctx.fillRect(0, 0, targetWidth, targetHeight)

            // Calculate scaling to preserve aspect ratio (fit within target dimensions)
            const imgAspect = img.width / img.height
            const targetAspect = targetWidth / targetHeight
            
            let drawWidth = targetWidth
            let drawHeight = targetHeight
            let offsetX = 0
            let offsetY = 0

            if (imgAspect > targetAspect) {
                // Image is wider - fit to width
                drawHeight = targetWidth / imgAspect
                offsetY = (targetHeight - drawHeight) / 2
            } else {
                // Image is taller - fit to height
                drawWidth = targetHeight * imgAspect
                offsetX = (targetWidth - drawWidth) / 2
            }

            // Draw image centered, preserving aspect ratio
            ctx.drawImage(img, offsetX, offsetY, drawWidth, drawHeight)

            // This contains raw RGBA values in order: [r,g,b,a, r,g,b,a, ...]
            const { data } = ctx.getImageData(0, 0, targetWidth, targetHeight)
            resolve(data)
        }

        img.onerror = reject
        img.src = dataUrl
    })
}

function getGrayScale(r: number, g: number, b: number): number {
    return 0.299 * r + 0.587 * g + 0.114 * b
}

// Sobel edge detection for wall extraction
function detectEdges(pixels: Uint8ClampedArray, width: number, height: number, threshold: number): boolean[][] {
    const grayscale: number[][] = Array.from({ length: height }, () => Array(width).fill(0))
    const edges: boolean[][] = Array.from({ length: height }, () => Array(width).fill(false))

    // Convert to grayscale
    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            const idx = (y * width + x) * 4
            grayscale[y][x] = getGrayScale(pixels[idx], pixels[idx + 1], pixels[idx + 2])
        }
    }

    // Sobel operators for edge detection
    const sobelX = [
        [-1, 0, 1],
        [-2, 0, 2],
        [-1, 0, 1]
    ]
    const sobelY = [
        [-1, -2, -1],
        [0, 0, 0],
        [1, 2, 1]
    ]

    // Apply Sobel filter to detect edges
    for (let y = 1; y < height - 1; y++) {
        for (let x = 1; x < width - 1; x++) {
            let gx = 0
            let gy = 0

            for (let ky = -1; ky <= 1; ky++) {
                for (let kx = -1; kx <= 1; kx++) {
                    const val = grayscale[y + ky][x + kx]
                    gx += val * sobelX[ky + 1][kx + 1]
                    gy += val * sobelY[ky + 1][kx + 1]
                }
            }

            // Calculate edge magnitude
            const magnitude = Math.sqrt(gx * gx + gy * gy)
            edges[y][x] = magnitude > threshold
        }
    }

    return edges
}

// Morphological thinning to reduce walls to single-pixel width
// Uses Zhang-Suen thinning algorithm for proper skeletonization
function thinWalls(walls: boolean[][], width: number, height: number): boolean[][] {
    let result = walls.map(row => [...row])
    let changed = true
    let iterations = 0
    const maxIterations = 100 // Safety limit

    // Get 8-neighborhood values in order: top, top-right, right, bottom-right, bottom, bottom-left, left, top-left
    const getNeighbors = (x: number, y: number, grid: boolean[][]): boolean[] => {
        return [
            grid[y - 1]?.[x] ?? false,     // P2
            grid[y - 1]?.[x + 1] ?? false, // P3
            grid[y]?.[x + 1] ?? false,     // P4
            grid[y + 1]?.[x + 1] ?? false, // P5
            grid[y + 1]?.[x] ?? false,     // P6
            grid[y + 1]?.[x - 1] ?? false, // P7
            grid[y]?.[x - 1] ?? false,     // P8
            grid[y - 1]?.[x - 1] ?? false  // P9
        ]
    }

    // Count transitions from 0 to 1 in circular neighborhood
    const countTransitions = (neighbors: boolean[]): number => {
        let transitions = 0
        for (let i = 0; i < 8; i++) {
            if (!neighbors[i] && neighbors[(i + 1) % 8]) {
                transitions++
            }
        }
        return transitions
    }

    // Count number of true neighbors
    const countNeighbors = (neighbors: boolean[]): number => {
        return neighbors.filter(n => n).length
    }

    // Zhang-Suen thinning: two sub-iterations per main iteration
    while (changed && iterations < maxIterations) {
        changed = false
        const toRemove: Array<{ x: number; y: number }> = []

        // Sub-iteration 1
        for (let y = 1; y < height - 1; y++) {
            for (let x = 1; x < width - 1; x++) {
                if (!result[y][x]) continue

                const neighbors = getNeighbors(x, y, result)
                const B = countNeighbors(neighbors)
                const A = countTransitions(neighbors)

                const p2 = neighbors[0]  // top
                const p4 = neighbors[2]  // right
                const p6 = neighbors[4]  // bottom
                const p8 = neighbors[6]  // left

                // Zhang-Suen condition for sub-iteration 1
                if (
                    B >= 2 && B <= 6 &&
                    A === 1 &&
                    (!p2 || !p4 || !p6) &&  // P2 × P4 × P6 = 0
                    (!p4 || !p6 || !p8)     // P4 × P6 × P8 = 0
                ) {
                    toRemove.push({ x, y })
                }
            }
        }

        // Remove pixels marked in sub-iteration 1
        toRemove.forEach(({ x, y }) => {
            result[y][x] = false
            changed = true
        })

        // Sub-iteration 2
        toRemove.length = 0
        for (let y = 1; y < height - 1; y++) {
            for (let x = 1; x < width - 1; x++) {
                if (!result[y][x]) continue

                const neighbors = getNeighbors(x, y, result)
                const B = countNeighbors(neighbors)
                const A = countTransitions(neighbors)

                const p2 = neighbors[0]  // top
                const p4 = neighbors[2]  // right
                const p6 = neighbors[4]  // bottom
                const p8 = neighbors[6]  // left

                // Zhang-Suen condition for sub-iteration 2
                if (
                    B >= 2 && B <= 6 &&
                    A === 1 &&
                    (!p2 || !p4 || !p8) &&  // P2 × P4 × P8 = 0
                    (!p2 || !p6 || !p8)     // P2 × P6 × P8 = 0
                ) {
                    toRemove.push({ x, y })
                }
            }
        }

        // Remove pixels marked in sub-iteration 2
        toRemove.forEach(({ x, y }) => {
            result[y][x] = false
            changed = true
        })

        iterations++
    }

    return result
}

function getWallsFromImagePixels(pixels: Uint8ClampedArray, width: number, height: number, edgeThreshold: number): boolean[][] {
    // Use edge detection to find walls
    const edges = detectEdges(pixels, width, height, edgeThreshold)
    // Thin the edges to single-pixel width
    const thinned = edges //thinWalls(edges, width, height)
    
    // Flip vertically to fix orientation (read pixels bottom-to-top)
    const flipped: boolean[][] = Array.from({ length: height }, () => Array(width).fill(false))
    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            flipped[y][x] = thinned[height - 1 - y][x]
        }
    }
    
    return flipped
}

export async function generateAndPopulateMapFromImageData(
    imageData: string | null,
    width: number,
    height: number,
    currentRound: Round,
    edgeThreshold: number = 30
): Promise<null> {
    if (!imageData) return null

    const wallBrush = new WallsBrush(currentRound)
    const map = currentRound.map.staticMap
    const symmetry = map.symmetry
    
    const pixels = await getPixelDataFromDataURL(imageData, width, height)
    const walls = getWallsFromImagePixels(pixels, width, height, edgeThreshold)
    
    // Determine the base region based on symmetry type
    // We only extract from the base region, then symmetry will mirror it
    let maxX = width
    let maxY = height
    
    switch (symmetry) {
        case 0: // ROTATIONAL - extract from top-left quarter
            maxX = Math.floor(width / 2)
            maxY = Math.floor(height / 2)
            break
        case 1: // HORIZONTAL - extract from top half
            maxX = width
            maxY = Math.floor(height / 2)
            break
        case 2: // VERTICAL - extract from left half
            maxX = Math.floor(width / 2)
            maxY = height
            break
    }
    
    // Only place walls from the base region - apply() will handle mirroring via symmetry
    for (let y = 0; y < maxY; y++) {
        for (let x = 0; x < maxX; x++) {
            if (walls[y][x]) {
                // Use apply() instead of symmetricApply() to trigger symmetry
                wallBrush.apply(x, y, wallBrush.fields, true)
            }
        }
    }

    GameRenderer.fullRender()
    return null
}

export const GenerativeMapCreator: React.FC<{
    mapWidth: number
    mapHeight: number
    currentRound: Round | undefined
}> = ({ mapWidth, mapHeight, currentRound }) => {
    const [imageData, setImageData] = React.useState<string | null>(null)
    const [edgeThreshold, setEdgeThreshold] = React.useState(30)
    const round = currentRound

    if (!round) return null
    return (
        <>
            <div className="flex flex-col gap-2">
                <div className="flex flex-col">
                    <span className="mr-2 text-sm mb-1">Upload Image</span>
                    <input
                        type="file"
                        accept="image/*"
                        onChange={(e) => {
                            const file = e.target.files?.[0]

                            if (!file) return
                            const reader = new FileReader()
                            reader.readAsDataURL(file)

                            reader.onload = (event) => {
                                const imageData = event.target?.result as string | null
                                if (imageData) setImageData(imageData)
                            }
                        }}
                    />
                </div>
                <div className="flex flex-col mt-2">
                    <span className="text-sm mb-1">Edge Sensitivity: {edgeThreshold}</span>
                    <input
                        type="range"
                        min="10"
                        max="100"
                        value={edgeThreshold}
                        onChange={(e) => setEdgeThreshold(parseInt(e.target.value))}
                        className="w-full"
                    />
                    <span className="text-xs text-gray-500">Lower = more sensitive (detects more edges)</span>
                </div>
                <div className="flex flex-col">
                    <Button
                        className="mx-0"
                        onClick={() => {
                            generateAndPopulateMapFromImageData(imageData, mapWidth, mapHeight, round, edgeThreshold)
                        }}
                    >
                        Generate Map
                    </Button>
                </div>
            </div>
        </>
    )
}

