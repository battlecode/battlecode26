import Round from './Round'
import { schema } from 'battlecode-schema'
import { unionToAction } from 'battlecode-schema/js/battlecode/schema/action'
import assert, { match } from 'assert'
import * as renderUtils from '../util/RenderUtil'
import { vectorAdd, vectorLength, vectorMultiply, vectorSub, vectorMultiplyInPlace, Vector } from './Vector'
import Match from './Match'
import { getImageIfLoaded } from '../util/ImageLoader'
import { Colors } from '../colors'

type ActionUnion = Exclude<ReturnType<typeof unionToAction>, null>

export default class Actions {
    actions: Action<ActionUnion>[] = []

    constructor() {}

    applyTurnDelta(round: Round, turn: schema.Turn): void {
        const robotId = turn.robotId()

        if (turn.actionsLength() > 0) {
            for (let i = 0; i < turn.actionsTypeLength(); i++) {
                const actionType = turn.actionsType(i)!
                const action =
                    unionToAction(actionType, (obj) => turn.actions(i, obj)) ??
                    assert.fail(`Failed to parse action ${i} with type ${actionType} on round ${round.roundNumber}`)

                // TODO: think about revisiting this
                const actionClass =
                    ACTION_DEFINITIONS[actionType] ??
                    assert.fail(`Action ${actionType} not found in ACTION_DEFINITIONS`)
                const newAction = new actionClass(robotId, action)

                this.actions.push(newAction)
                newAction.apply(round)
            }
        }
    }

    tickLifetimes(round: Round): void {
        // Tick lifetimes of applied actions
        for (let i = 0; i < this.actions.length; i++) {
            this.actions[i].duration--
            if (this.actions[i].duration == 0) {
                // If action render order matters, use this (slower)
                //this.actions.splice(i, 1)

                // Otherwise, this is faster
                [this.actions[i], this.actions[this.actions.length-1]] = [this.actions[this.actions.length - 1], this.actions[i]]
                this.actions[this.actions.length-1].finish(round)
                this.actions.pop()

                i--
            }
        }
    }

    copy(): Actions {
        const newActions = new Actions()
        newActions.actions = this.actions.map((action) => action.copy())
        return newActions
    }

    draw(match: Match, ctx: CanvasRenderingContext2D) {
        for (const action of this.actions) {
            action.draw(match, ctx)
        }
    }
}

export class Action<T extends ActionUnion> {
    constructor(
        protected robotId: number,
        protected actionData: T,
        public duration: number = 1
    ) {}

    /**
     * Applies this action to the round provided. If stat is provided, it will be mutated to reflect the action as well
     *
     * @param round the round to apply this action to
     * @param stat if provided, this action will mutate the stat to reflect the action
     */
    apply(round: Round): void {}

    draw(match: Match, ctx: CanvasRenderingContext2D) {}

    finish(round: Round): void {}

    copy(): Action<T> {
        // creates a new object using this object's prototype and all its parameters. this is a shallow copy, override this if you need a deep copy
        return Object.create(Object.getPrototypeOf(this), Object.getOwnPropertyDescriptors(this))
    }
}

export const ACTION_DEFINITIONS: Record<schema.Action, typeof Action<ActionUnion>> = {
    [schema.Action.NONE]: class NONE extends Action<ActionUnion> {
        apply(round: Round): void {
            throw new Error("yoo what !?! this shouldn't happen! :( (NONE action)")
        }
    },
    [schema.Action.CatFeed]: class CatFeedAction extends Action<schema.CatFeed> {
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            // chomping animation
            const src = match.currentRound.bodies.getById(this.robotId) // cat
            // const target = match.currentRound.bodies.getById(this.actionData.id()) // rat being eaten
            // const coords = renderUtils.getRenderCoords(src.pos.x, src.pos.y, match.map.dimension, true)
            // const random1 = ((src.pos.x * 491 + src.pos.y * 603 + match.currentRound.roundNumber * 343) / 100) % 1 // https://xkcd.com/221/
            // const random2 = ((src.pos.x * 259 + src.pos.y * 429 + match.currentRound.roundNumber * 224) / 100) % 1
            // const interpolationFactor = match.getInterpolationFactor()

            // ctx.save()
            // ctx.globalAlpha = 0.5 - 0.5 * interpolationFactor * interpolationFactor
            // ctx.fillStyle = '#000000'
            // ctx.font = '0.4px Arial'
            // // parabolic trajectory.
            // const fontX = coords.x + (4 * random1 - 2) * interpolationFactor - 0.5
            // const fontY =
            //     coords.y - (2 + 4 * random2) * interpolationFactor + 8 * interpolationFactor * interpolationFactor - 0.5
            // ctx.fillText('nom', fontX, fontY)
            src.textureOverride = true
            src.imgPath = `robots/cat/cat_feed_${src.direction}.png`// is reset in `finish`.
            // ctx.restore()
        }

        finish(round: Round): void {
            const src = round.bodies.getById(this.robotId)
            src.textureOverride = false
        }
    },
    [schema.Action.RatAttack]: class AttackAction extends Action<schema.RatAttack> {
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            const srcBody = match.currentRound.bodies.getById(this.robotId)
            const dstBody = match.currentRound.bodies.getById(this.actionData.id())

            if (!dstBody) return

            const from = srcBody.getInterpolatedCoords(match)
            const to = dstBody.getInterpolatedCoords(match)

            // Compute the start and end points for the animation projectile
            const dir = vectorSub(to, from)
            const len = vectorLength(dir)
            vectorMultiplyInPlace(dir, 1 / len)
            const projectileStart = vectorAdd(from, vectorMultiply(dir, len * match.getInterpolationFactor()))
            const projectileEnd = vectorAdd(
                from,
                vectorMultiply(dir, len * Math.min(match.getInterpolationFactor() + 0.2, 1.0))
            )

            // True direction
            renderUtils.renderLine(
                ctx,
                renderUtils.getRenderCoords(from.x, from.y, match.currentRound.map.staticMap.dimension),
                renderUtils.getRenderCoords(to.x, to.y, match.currentRound.map.staticMap.dimension),
                {
                    teamForOffset: srcBody.team,
                    color: srcBody.team.color,
                    lineWidth: 0.06,
                    opacity: 0.5,
                    renderArrow: false
                }
            )

            // Projectile animation
            renderUtils.renderLine(
                ctx,
                renderUtils.getRenderCoords(
                    projectileStart.x,
                    projectileStart.y,
                    match.currentRound.map.staticMap.dimension
                ),
                renderUtils.getRenderCoords(
                    projectileEnd.x,
                    projectileEnd.y,
                    match.currentRound.map.staticMap.dimension
                ),
                {
                    teamForOffset: srcBody.team,
                    color: srcBody.team.color,
                    lineWidth: 0.06,
                    opacity: 1.0,
                    renderArrow: false
                }
            )
        }
    },
    [schema.Action.RatNap]: class RatNapAction extends Action<schema.RatNap> {
        // private static readonly OFFSET = { x: -0.35, y: 0 }
        apply(round: Round): void {
            // move the target onto the source adjust target's size using scale factor
            const src = round.bodies.getById(this.robotId)
            const target = round.bodies.getById(this.actionData.id()) // rat getting napped
            
            if (target.beingCarried) {
                // drop the target
                // const carrier = round.bodies.getById(target.carrierRobot!)
                if(target.carrierRobot !== undefined) {
                    const carrier = round.bodies.getById(target.carrierRobot)
                    carrier.carriedRobot = undefined
                }
                target.size = 1
                target.beingCarried = false
                target.carrierRobot = undefined
            } else {
                // pick up the target
                src.carriedRobot = target.id
                target.carrierRobot = src.id
                target.carriedRobot = undefined
                target.beingCarried = true

                target.lastPos = { ...target.pos }
                // target.pos = { x: src.pos.x + RatNapAction.OFFSET.x, y: src.pos.y + RatNapAction.OFFSET.y }
                target.size = 0.6
            }
        }
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            //target rat moves onto src rat, circle around carried group thing
            const src = match.currentRound.bodies.getById(this.robotId) 
            const srcCoords = renderUtils.getRenderCoords(src.pos.x, src.pos.y, match.map.dimension, true)
            const t = match.getInterpolationFactor()
            const bump = Math.sin(t * Math.PI * 8) * 0.03
            const half = 0.5 + bump
            const radius = 0.08 // corner radius

            ctx.save()
            ctx.shadowBlur = 12
            ctx.shadowColor = src.team.color
            ctx.strokeStyle = src.team.color
            ctx.globalAlpha = 0.7
            ctx.lineWidth = 0.04
            ctx.beginPath()
            ctx.moveTo(srcCoords.x - half + radius, srcCoords.y - half)
            ctx.arcTo(srcCoords.x + half, srcCoords.y - half, srcCoords.x + half, srcCoords.y + half, radius)
            ctx.arcTo(srcCoords.x + half, srcCoords.y + half, srcCoords.x - half, srcCoords.y + half, radius)
            ctx.arcTo(srcCoords.x - half, srcCoords.y + half, srcCoords.x - half, srcCoords.y - half, radius)
            ctx.arcTo(srcCoords.x - half, srcCoords.y - half, srcCoords.x + half, srcCoords.y - half, radius)
            ctx.stroke()
            ctx.restore()
        }
    },
    [schema.Action.RatCollision]: class RatCollisionAction extends Action<schema.RatCollision> {
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            // animation for colliding time (rubble or something)
            const src = match.currentRound.bodies.getById(this.robotId)
            const pos = match.map.indexToLocation(this.actionData.loc())
            const coords = renderUtils.getRenderCoords(pos.x, pos.y, match.map.dimension, true)
            const t = match.getInterpolationFactor()

            ctx.save()
            // dusty base color that fills the cell and fades out
            const baseAlpha = 0.4 * (1 - t)
            ctx.fillStyle = `rgba(150,130,110,${baseAlpha})`
            ctx.fillRect(coords.x - 0.5, coords.y - 0.5, 1, 1)

            // these are the random rocks that fill the cell
            const rockCount = 10
            for (let i = 0; i < rockCount; i++) {
                const rx = coords.x - 0.5 + Math.random() * 1
                const ry = coords.y - 0.5 + Math.random() * 1
                const size = 0.08 + Math.random() * 0.15
                const shade = 90 + Math.floor(Math.random() * 50)
                const alpha = 0.7 * (1 - t)
                ctx.fillStyle = `rgba(${shade},${shade - 10},${shade - 20},${alpha})`
                ctx.fillRect(rx, ry, size, size)
            }

            // ring outside the cell (also fades out)
            ctx.strokeStyle = src.team.color
            ctx.globalAlpha = 0.35 * (1 - t)
            ctx.lineWidth = 0.04
            ctx.strokeRect(coords.x - 0.5, coords.y - 0.5, 1, 1)
            ctx.restore()

        }
    },
    [schema.Action.PlaceDirt]: class PlaceDirtAction extends Action<schema.PlaceDirt> {
        apply(round: Round): void {
            // make dirt boolean
            round.map.dirt[this.actionData.loc()] = 1
        }
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            const body = match.currentRound.bodies.getById(this.robotId)
            const pos = body.getInterpolatedCoords(match)

            const target = match.map.indexToLocation(this.actionData.loc())
            renderUtils.renderLine(
                ctx,
                renderUtils.getRenderCoords(pos.x, pos.y, match.currentRound.map.staticMap.dimension),
                renderUtils.getRenderCoords(target.x, target.y, match.currentRound.map.staticMap.dimension),
                {
                    color: Colors.DIRT_COLOR.get(),
                    lineWidth: 0.04,
                    opacity: 0.4
                }
            )
        }
    },
    [schema.Action.BreakDirt]: class BreakDirtAction extends Action<schema.BreakDirt> {
        apply(round: Round): void {
            // remove the dirt
            const pos = round.map.indexToLocation(this.actionData.loc())

            round.map.dirt[this.actionData.loc()] = 0
        }
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            // dirt breaking animation
            const pos = match.map.indexToLocation(this.actionData.loc())
            const coords = renderUtils.getRenderCoords(pos.x, pos.y, match.map.dimension, true)
        }
    },
    [schema.Action.CheesePickup]: class CheesePickupAction extends Action<schema.CheesePickup> {
        apply(round: Round): void {
            // remove cheese from map and increment body cheese count
            const body = round.bodies.getById(this.robotId)
            const amt = round.map.cheeseData[this.actionData.loc()]
            round.map.cheeseData[this.actionData.loc()] = 0
            body.cheese += amt
        }
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            // cheese pickup animation
            const map = match.currentRound.map
            const body = match.currentRound.bodies.getById(this.robotId)
            const coords = renderUtils.getRenderCoords(body.pos.x, body.pos.y, map.dimension, false)
            const factor = match.getInterpolationFactor()
            const isEndpoint = factor == 0 || factor == 1
            const size = isEndpoint ? 1 : Math.max(factor * 1.5, 0.3)
            const alpha = isEndpoint ? 1 : (factor < 0.5 ? factor : 1 - factor) * 2

            ctx.globalAlpha = alpha
            ctx.shadowBlur = 4
            ctx.shadowColor = 'black'
            renderUtils.renderCenteredImageOrLoadingIndicator(
                ctx,
                getImageIfLoaded('icons/cheese_64x64.png'),
                coords,
                size
            )
            ctx.shadowBlur = 0
            ctx.shadowColor = ''
            ctx.globalAlpha = 1
        }
    },
    [schema.Action.CheeseSpawn]: class CheeseSpawnAction extends Action<schema.CheeseSpawn> {
        apply(round: Round): void {
            // add cheese to map
            const amount = this.actionData.amount()

            round.map.cheeseData[this.actionData.loc()] = amount
        }
    },
    [schema.Action.CheeseTransfer]: class CheeseTransferAction extends Action<schema.CheeseTransfer> {
        apply(round: Round): void {
            // transfer cheese between bots
            const body = round.bodies.getById(this.robotId)
            const target = round.bodies.getById(this.actionData.id())
            const amount = this.actionData.amount()

            body.cheese -= Math.min(body.cheese, amount)
            target.cheese += Math.min(body.cheese, amount)
        }
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            const srcBody = match.currentRound.bodies.getById(this.robotId)
            const targetBody = match.currentRound.bodies.getById(this.actionData.id())

            if (!targetBody) return

            const from = srcBody.getInterpolatedCoords(match)
            const to = targetBody.getInterpolatedCoords(match)

            const progress = match.getInterpolationFactor()

            const currentX = from.x + (to.x - from.x) * progress
            const currentY = from.y + (to.y - from.y) * progress

            const coords = renderUtils.getRenderCoords(currentX, currentY, match.map.dimension)

            const cheeseImage = getImageIfLoaded('icons/cheese_64x64.png')
            if (cheeseImage) {
                const size = 0.8
                renderUtils.renderCenteredImageOrLoadingIndicator(ctx, cheeseImage, coords, size)
            }

            renderUtils.renderLine(
                ctx,
                renderUtils.getRenderCoords(from.x, from.y, match.currentRound.map.staticMap.dimension),
                renderUtils.getRenderCoords(to.x, to.y, match.currentRound.map.staticMap.dimension),
                {
                    color: '#f7df47',
                    lineWidth: 0.06,
                    opacity: 0.8
                }
            )
        }
    },
    [schema.Action.CatScratch]: class CatScratchAction extends Action<schema.CatScratch> {
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            // cat scratching animation
            const body = match.currentRound.bodies.getById(this.robotId)
            const pos = match.map.indexToLocation(this.actionData.loc())
            const coords = renderUtils.getRenderCoords(pos.x, pos.y, match.map.dimension, true)
            
            const dir = body.direction
            body.textureOverride = true
            body.imgPath = `robots/cat/cat_scratch_${dir}.png`
            
            const reflected = body.pos.x < pos.x

            const interpolationFactor = match.getInterpolationFactor()
            ctx.strokeStyle = body.team.color
            ctx.globalAlpha = 0.6
            ctx.fillStyle = body.team.color
            ctx.beginPath()
            if (reflected) {
                ctx.arc(coords.x, coords.y, 0.5, -1 - 2 * interpolationFactor, -2 * interpolationFactor)
                ctx.arc(coords.x - 0.1, coords.y + 0.1, 0.5, -1 - 2 * interpolationFactor, -2 * interpolationFactor)
                ctx.arc(coords.x + 0.1, coords.y - 0.1, 0.5, -1 - 2 * interpolationFactor, -2 * interpolationFactor)
            } else {
                ctx.arc(coords.x, coords.y, 0.5, 2 * interpolationFactor, 1 + 2 * interpolationFactor)
                ctx.arc(coords.x + 0.1, coords.y + 0.1, 0.5, 2 * interpolationFactor, 1 + 2 * interpolationFactor)
                ctx.arc(coords.x - 0.1, coords.y - 0.1, 0.5, 2 * interpolationFactor, 1 + 2 * interpolationFactor)
            }
            ctx.stroke()
            ctx.globalAlpha = 1
        }

        finish(round: Round): void {
            const body = round.bodies.getById(this.robotId)
            body.textureOverride = false
        }
    },
    [schema.Action.CatPounce]: class CatPounceAction extends Action<schema.CatPounce> {
        apply(round: Round): void {
            // maybe move cat to target loc
            const body = round.bodies.getById(this.robotId)
            const startPos = round.map.indexToLocation(this.actionData.startLoc())
            const endPos = round.map.indexToLocation(this.actionData.endLoc())
            console.log('pounce from', startPos, 'to', endPos)
        }
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            // cat pouncing animation
            const body = match.currentRound.bodies.getById(this.robotId)
            const startPos = match.map.indexToLocation(this.actionData.startLoc())
            const endPos = match.map.indexToLocation(this.actionData.endLoc())
            const startCoords = renderUtils.getRenderCoords(startPos.x, startPos.y, match.map.dimension, true)
            const endCoords = renderUtils.getRenderCoords(endPos.x, endPos.y, match.map.dimension, true)
            const angle = Math.atan2(endPos.y - startPos.y, endPos.x - startPos.x)

            body.textureOverride = true
            let texture: string
            if (angle >= (7 * Math.PI) / 4 || angle <= Math.PI / 4) {
                texture = 'robots/cat/cat_pounce_5.png'
            } else if (angle > Math.PI / 4 && angle < (3 * Math.PI) / 4) {
                texture = 'robots/cat/cat_pounce_7.png'
            } else if (angle >= (3 * Math.PI) / 4 && angle <= (5 * Math.PI) / 4) {
                texture = 'robots/cat/cat_pounce_1.png'
            } else {
                texture = 'robots/cat/cat_pounce_3.png'
            }
            body.imgPath = texture

            const gravity: number = -10
            const interpolationFactor = match.getInterpolationFactor()
            const catX = startPos.x + interpolationFactor * (endPos.x - startPos.x)
            const catY =
                gravity * interpolationFactor * interpolationFactor +
                interpolationFactor * (endPos.y - startPos.y - gravity) +
                startPos.y
            body.pos = { x: catX, y: catY }
        }
        finish(round: Round): void {
            const body = round.bodies.getById(this.robotId)
            body.textureOverride = false
        }
    },
    [schema.Action.PlaceTrap]: class PlaceTrapAction extends Action<schema.PlaceTrap> {
        apply(round: Round): void {
            // add a trap to map
            const body = round.bodies.getById(this.robotId)

            if (this.actionData.isRatTrapType()) {
                round.map.ratTrapData[this.actionData.loc()] = 1 + body.team.id // 1 for team 0, 2 for team 1
            } else {
                round.map.catTrapData[this.actionData.loc()] = 1 + body.team.id // 1 for team 0, 2 for team 1
            }
        }
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            // place trap animation
            const to = match.map.indexToLocation(this.actionData.loc())
            const coords = renderUtils.getRenderCoords(to.x, to.y, match.map.dimension, false)
            const factor = match.getInterpolationFactor()
            const isEndpoint = factor == 0 || factor == 1
            const size = isEndpoint ? 1 : Math.max(factor * 1.5, 0.3)
            const alpha = isEndpoint ? 1 : (factor < 0.5 ? factor : 1 - factor) * 2
            let imgPath: string
            if (this.actionData.isRatTrapType()) {
                imgPath = 'icons/rat_trap.png'
            } else {
                imgPath = 'icons/cat_trap.png'
            }

            ctx.globalAlpha = alpha
            ctx.shadowBlur = 4
            ctx.shadowColor = 'black'
            renderUtils.renderCenteredImageOrLoadingIndicator(ctx, getImageIfLoaded(imgPath), coords, size)
            ctx.shadowBlur = 0
            ctx.shadowColor = ''
            ctx.globalAlpha = 1
        }
    },
    [schema.Action.RemoveTrap]: class RemoveTrapAction extends Action<schema.RemoveTrap> {
        apply(round: Round): void {
            // remove a trap from map
            round.map.ratTrapData[this.actionData.loc()] = 0
            round.map.catTrapData[this.actionData.loc()] = 0
        }
    },
    [schema.Action.TriggerTrap]: class TriggerTrapAction extends Action<schema.TriggerTrap> {
        apply(round: Round): void {
            // remove trap from map
            round.map.ratTrapData[this.actionData.loc()] = 0 // remove trap
            round.map.catTrapData[this.actionData.loc()] = 0 // remove trap
        }
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            // trap triggering animation
            const body = match.currentRound.bodies.getById(this.robotId)
            // const pos = match.map.indexToLocation(this.actionData.loc())
            const pos = body.getInterpolatedCoords(match)
            const coords = renderUtils.getRenderCoords(pos.x, pos.y, match.map.dimension, true)

            const size = body.size - 1

            const t = match.getInterpolationFactor()
            const snap = Math.pow(t, 0.25)
            const rotation = -snap * (Math.PI / 2)

            ctx.save()
            ctx.translate(coords.x + size * 0.5, coords.y + 0.5)
            ctx.strokeStyle = body.team.color
            ctx.lineWidth = 0.08
            ctx.lineCap = 'round'

            ctx.beginPath()
            ctx.moveTo(-0.1, 0)
            ctx.lineTo(0.1, 0)
            ctx.stroke()

            ctx.save()
            ctx.rotate(-rotation)
            ctx.beginPath()
            ctx.moveTo(0, 0)
            ctx.lineTo(-0.6 - size * 0.5, 0)
            ctx.lineTo(-0.6 - size * 0.5, -0.1)
            ctx.stroke()
            ctx.restore()

            ctx.save()
            ctx.rotate(rotation)
            ctx.beginPath()
            ctx.moveTo(0, 0)
            ctx.lineTo(0.6 + size * 0.5, 0)
            ctx.lineTo(0.6 + size * 0.5, -0.1)
            ctx.stroke()
            ctx.restore()

            ctx.restore()
        }
    },
    [schema.Action.ThrowRat]: class ThrowRatAction extends Action<schema.ThrowRat> {
        apply(round: Round): void {
            // maybe move rat to target loc
            const body = round.bodies.getById(this.actionData.id())
            const endLoc = round.map.indexToLocation(this.actionData.loc())
            if( body.carrierRobot !== undefined && round.bodies.hasId(body.carrierRobot)) {
                const carrier = round.bodies.getById(body.carrierRobot)
                carrier.carriedRobot = undefined
            }
            body.carrierRobot = undefined
            body.beingCarried = false
            body.size = 1
            // body.pos = { ...endLoc }
        }
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            if( !match.currentRound.bodies.hasId(this.actionData.id()) ) return
            const body = match.currentRound.bodies.getById(this.actionData.id())
            const pos = body.getInterpolatedCoords(match)
            const coords = renderUtils.getRenderCoords(pos.x, pos.y, match.map.dimension, true)
            const interp = match.getInterpolationFactor()

            const from = pos
            const to = match.currentRound.map.indexToLocation(this.actionData.loc())

            const dx = to.x - from.x
            const dy = to.y - from.y
            const mag = Math.hypot(dx, dy)
            if (mag < 1e-3) return

            const ux = dx / mag
            const uy = dy / mag
            const px = uy
            const py = ux

            // deterministic jitter
            const r = ((from.x * 317 + from.y * 911 + match.currentRound.roundNumber * 271) / 100) % 1

            ctx.save()

            ctx.globalAlpha = 0.6 * (1 - interp)
            ctx.strokeStyle = '#000000'
            ctx.lineCap = 'round'
            ctx.lineWidth = 0.1

            const baseLength = 0.6 + 0.3 * interp
            const spacing = 0.2

            for (let i = -1; i <= 1; i++) {
                const offset = i * spacing
                const jitter = (r - 0.5) * 0.15

                const endX = coords.x + ux * 0.3 + px * offset + px * jitter
                const endY = coords.y - uy * 0.3 + py * offset + py * jitter

                const startX = endX + ux * baseLength
                const startY = endY - uy * baseLength

                ctx.beginPath()
                ctx.moveTo(startX, startY)
                ctx.lineTo(endX, endY)
                ctx.stroke()
            }

            ctx.restore()
        }
    },
    [schema.Action.UpgradeToRatKing]: class UpgradeToRatKingAction extends Action<schema.UpgradeToRatKing> {
        apply(round: Round): void {
            // promote body in-place to RatKing while preserving ID/state
            const body = round.bodies.getById(this.robotId)
            body.promoteTo(schema.RobotType.RAT_KING)
        }
        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            const body = match.currentRound.bodies.getById(this.robotId)
            const pos = body.getInterpolatedCoords(match)
            const coords = renderUtils.getRenderCoords(pos.x, pos.y, match.map.dimension, true)

            const interp = match.getInterpolationFactor()
            const pop = Math.sin(interp * Math.PI)

            const radius = 0.5
            const alpha = 0.5 * (1 - interp)

            const seed = ((pos.x * 413 + pos.y * 619 + match.currentRound.roundNumber * 911) / 100) % 1

            ctx.save()
            ctx.globalAlpha = alpha
            ctx.fillStyle = '#ffffff'
            ctx.beginPath()
            ctx.arc(coords.x, coords.y, radius, 0, Math.PI * 2)

            const spikeCount = 30
            const baseSpikeLen = 0.1 + 0.1 * pop

            ctx.strokeStyle = '#ffffff'
            ctx.lineWidth = 0.06
            ctx.lineCap = 'round'

            for (let i = 0; i < spikeCount; i++) {
                const angle = (i / spikeCount) * Math.PI * 2 + seed * Math.PI * 0.4

                const len = baseSpikeLen * (0.6 + 0.6 * Math.abs(Math.sin(i * 73.1 + seed * 19)))

                const x1 = coords.x + Math.cos(angle) * radius
                const y1 = coords.y + Math.sin(angle) * radius
                const x2 = coords.x + Math.cos(angle) * (radius + len)
                const y2 = coords.y + Math.sin(angle) * (radius + len)

                ctx.beginPath()
                ctx.moveTo(x1, y1)
                ctx.lineTo(x2, y2)
                ctx.stroke()
            }

            ctx.restore()
        }
    },
    [schema.Action.RatSqueak]: class RatSqueakAction extends Action<schema.RatSqueak> {
        // TODO

        draw(match: Match, ctx: CanvasRenderingContext2D): void {
            const body = match.currentRound.bodies.getById(this.robotId)
            const renderCoords = renderUtils.getRenderCoords(
                body.pos.x,
                body.pos.y,
                match.map.dimension,
            )
            renderUtils.renderCenteredImageOrLoadingIndicator(
                ctx,
                getImageIfLoaded('robots/squeak.png'),
                renderCoords,
                body.size
            )
        }
    },
    [schema.Action.DamageAction]: class DamageAction extends Action<schema.DamageAction> {
        apply(round: Round): void {
            const src = round.bodies.getById(this.robotId)
            const target = round.bodies.getById(this.actionData.id())

            if (!target) return

            const damage = this.actionData.damage()
            target.hp = Math.max(target.hp - damage, 0)
        }
    },
    [schema.Action.StunAction]: class StunAction extends Action<schema.StunAction> {
        // TODO
    },
    [schema.Action.SpawnAction]: class SpawnAction extends Action<schema.SpawnAction> {
        apply(round: Round): void {
            round.bodies.spawnBodyFromAction(this.actionData)
        }
    },
    [schema.Action.DieAction]: class DieAction extends Action<schema.DieAction> {
        apply(round: Round): void {
            if (this.actionData.dieType() === schema.DieType.EXCEPTION) {
                // TODO: revisit this
                console.log(`Robot ${this.robotId} has died due to an exception`)
            }

            round.bodies.markBodyAsDead(this.actionData.id())
        }
    },
    [schema.Action.IndicatorStringAction]: class IndicatorStringAction extends Action<schema.IndicatorStringAction> {
        apply(round: Round): void {
            const body = round.bodies.getById(this.robotId)
            const string = this.actionData.value()!
            body.indicatorString = string
        }
    },
    [schema.Action.IndicatorDotAction]: class IndicatorDotAction extends Action<schema.IndicatorDotAction> {
        apply(round: Round): void {
            const loc = this.actionData.loc()
            const vectorLoc = round.map.indexToLocation(loc)

            const body = round.bodies.getById(this.robotId)
            body.indicatorDots.push({
                location: vectorLoc,
                color: renderUtils.colorToHexString(this.actionData.colorHex())
            })
        }
    },
    [schema.Action.IndicatorLineAction]: class IndicatorLineAction extends Action<schema.IndicatorLineAction> {
        apply(round: Round): void {
            const starts = round.map.indexToLocation(this.actionData.startLoc())
            const ends = round.map.indexToLocation(this.actionData.endLoc())

            const body = round.bodies.getById(this.robotId)
            body.indicatorLines.push({
                start: starts,
                end: ends,
                color: renderUtils.colorToHexString(this.actionData.colorHex())
            })
        }
    }
}
