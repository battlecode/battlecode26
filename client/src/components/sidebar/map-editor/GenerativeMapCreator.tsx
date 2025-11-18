import { Button } from '../../button'
import { SymmetricMapEditorBrush } from './MapEditorBrush'
import React from 'react'
import { WallsBrush } from '../../../playback/Brushes'
import Round from '../../../playback/Round'
import { StaticMap } from '../../../playback/Map'
import { GameRenderer } from '../../../playback/GameRenderer'

async function getPixelDataFromDataURL(dataUrl: string): Promise<Uint8ClampedArray> {
    return new Promise((resolve, reject) => {
        const img = new Image()
        img.crossOrigin = 'anonymous' // avoids some browser restrictions

        img.onload = () => {
            const canvas = document.createElement('canvas')
            canvas.width = img.width
            canvas.height = img.height

            const ctx = canvas.getContext('2d')
            if (!ctx) return reject('Could not get 2D context')

            ctx.drawImage(img, 0, 0)

            // This contains raw RGBA values in order: [r,g,b,a, r,g,b,a, ...]
            const { data } = ctx.getImageData(0, 0, img.width, img.height)
            resolve(data)
        }

        img.onerror = reject
        img.src = dataUrl
    })
}

function getWallsFromImagePixels(pixels: Uint8ClampedArray, width: number, height: number): boolean[][] {
    const walls: boolean[][] = Array.from({ length: height }, () => Array(width).fill(false))

    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            const index = (y * width + x) * 4
            const [r, g, b, a] = [pixels[index], pixels[index + 1], pixels[index + 2], pixels[index + 3]]

            // dark = wall
            walls[y][x] = (r + g + b) / 3 < 50
        }
    }

    return walls
}

export async function generateAndPopulateMapFromImageData(
    imageData: string | null,
    width: number,
    height: number,
    currentRound: Round
): Promise<null> {
    if (!imageData) return null

    const wallBrush = new WallsBrush(currentRound)
    const pixels = await getPixelDataFromDataURL(imageData)
    const walls = getWallsFromImagePixels(pixels, width, height)
    for (let y = 0; y < walls.length; y++) {
        for (let x = 0; x < walls[y].length; x++) {
            if (walls[y][x]) {
                wallBrush.symmetricApply(x, y, wallBrush.fields)
            }
        }
    }

    return null
}

export const GenerativeMapCreator: React.FC<{
    mapWidth: number
    mapHeight: number
    currentRound: Round | undefined
}> = ({ mapWidth, mapHeight, currentRound }) => {
    const [imageData, setImageData] = React.useState<string | null>(null)
    const round = currentRound

    if (!round) return null
    return (
        <>
            <div className="flex flex-col">
                <span className="mr-2 text-sm">Upload Image</span>
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
            <div className="flex flex-col">
                <Button
                    className="mx-0"
                    onClick={() => {
                        // Trigger map generation logic
                        generateAndPopulateMapFromImageData(imageData, mapWidth, mapHeight, round)
                        GameRenderer.fullRender()
                    }}
                >
                    Generate Map
                </Button>
            </div>
        </>
    )
}
