import React from 'react'
import { TeamTable } from './team-table'
import { ResourceGraph } from './resource-graph'
import { useSearchParamBool } from '../../../app-search-params'
import { useAppContext } from '../../../app-context'
import { SectionHeader } from '../../section-header'
import { Crown } from '../../../icons/crown'
import { BiMedal } from 'react-icons/bi'
import Tooltip from '../../tooltip'
import Match from '../../../playback/Match'
import { Team } from '../../../playback/Game'
import { useGame, useRound } from '../../../playback/GameRunner'

const NO_GAME_TEAM_NAME = '?????'

interface Props {
    open: boolean
}

export const GamePage: React.FC<Props> = React.memo((props) => {
    const context = useAppContext()
    const game = useGame()
    const round = useRound()

    const [showStats, setShowStats] = useSearchParamBool('showStats', true)

    if (!props.open) return null

    const getWinCount = (team: Team) => {
        // Only return up to the current match if tournament mode is enabled
        if (!game) return 0
        let stopCounting = false
        const isWinner = (match: Match) => {
            if (context.state.tournament && stopCounting) return 0
            if (match == game.currentMatch) {
                stopCounting = true
                // Dont include this match if we aren't at the end yet
                if (context.state.tournament && !match.currentRound.isEnd()) return 0
            }
            return match.winner?.id === team.id ? 1 : 0
        }
        return game.matches.reduce((val, match) => val + isWinner(match), 0)
    }

    const teamBox = (teamIdx: number) => {
        const winCount = game ? getWinCount(game.teams[teamIdx]) : 0
        const isEndOfMatch = round?.isEnd()

        let showMatchWinner = !context.state.tournament || isEndOfMatch
        showMatchWinner = showMatchWinner && !!game && game.currentMatch?.winner === game.teams[teamIdx]
        let showGameWinner = !context.state.tournament || (showMatchWinner && winCount >= 3)
        showGameWinner = showGameWinner && !!game && game.winner === game.teams[teamIdx]

        return (
            <div
                className={
                    'relative w-full py-2 px-3 text-black text-center ' + (teamIdx == 0 ? 'bg-team0' : 'bg-team1')
                }
            >
                <div>{game?.teams[teamIdx].name ?? NO_GAME_TEAM_NAME}</div>
                <div className="absolute top-2 left-3">
                    <div className="relative flex items-center w-[24px] h-[24px]">
                        {showMatchWinner && (
                            <div className="absolute">
                                <Tooltip text={'Current match winner'} location={'right'}>
                                    <BiMedal
                                        opacity={0.5}
                                        fontSize={'24px'}
                                        width={'20px'}
                                        color={'#ffd43b'}
                                        strokeWidth={'1px'}
                                        stroke="#7f6a1d"
                                    />
                                </Tooltip>
                            </div>
                        )}
                        <div
                            className="absolute w-full text-sm pointer-events-none z-5"
                            style={{ textShadow: 'white 0px 0px 4px' }}
                        >
                            {winCount > 0 && winCount}
                        </div>
                    </div>
                </div>
                <div className="absolute top-3 right-3">
                    {showGameWinner && (
                        <Tooltip text={'Overall game winner'} location={'left'}>
                            <Crown className="opacity-50" />
                        </Tooltip>
                    )}
                </div>
            </div>
        )
    }

    const isGameModeCooperation =
        (round ?? true) &&
        !!game &&
        (() => {
            const t0 = round?.stat.getTeamStat(game.teams[0])?.gameModeCooperation ?? true
            const t1 = round?.stat.getTeamStat(game.teams[1])?.gameModeCooperation ?? true
            return t0 && t1
        })()

    return (
        <div className="flex flex-col overflow-x-hidden">
            <div className="w-full pb-3 px-4 text-center">
                {game && game.currentMatch && (
                    <div className="border-white border rounded-md font-bold">{game.currentMatch.map.name}</div>
                )}
            </div>
            {game ? (
                isGameModeCooperation ? (
                    <div className="w-full flex justify-center my-2">
                        <div className="px-3 py-1 rounded-md bg-lime-500 text-black font-bold">
                            Cooperation Mode Active
                        </div>
                    </div>
                ) : (
                    <div className="w-full flex justify-center my-2">
                        <div className="px-3 py-1 rounded-md bg-rose-600 text-black font-bold">
                            Backstab Mode Active
                        </div>
                    </div>
                )
            ) : null}
            {teamBox(0)}
            <TeamTable teamIdx={0} />

            <div className="h-[15px] min-h-[15px]" />

            {teamBox(1)}
            <TeamTable teamIdx={1} />

            <SectionHeader
                title="Stats"
                open={showStats}
                onClick={() => setShowStats(!showStats)}
                containerClassName="mt-2"
                titleClassName="py-2"
            >
                {game && game.playable ? (
                    <div /*className="flex items-center gap-2"*/>
                        {/* Note: to keep animation smooth, we should still keep the elements rendered, but we pass showStats into
                            them so that they don't render any data (since we're likely hiding stats to prevent lag) */}
                        <ResourceGraph
                            active={showStats}
                            property="cheeseAmount"
                            propertyDisplayName="Cheese Amount "
                        />
                        <br />
                        <ResourceGraph
                            active={showStats}
                            property="cheesePercent"
                            propertyDisplayName="Cheese Percent "
                        />
                        <br />
                        <ResourceGraph active={showStats} property="catDamageAmount" propertyDisplayName="Cat Damage" />
                        <br />
                        <ResourceGraph
                            active={showStats}
                            property="catDamagePercent"
                            propertyDisplayName="Cat Damage Percent"
                        />
                        <ResourceGraph
                            active={showStats}
                            property="ratKingCount"
                            propertyDisplayName="Rat King Count"
                        />
                        <br />
                        <ResourceGraph
                            active={showStats}
                            property="ratKingPercent"
                            propertyDisplayName="Rat King Percent"
                        />
                    </div>
                ) : (
                    <div>Select a game to see stats</div>
                )}
            </SectionHeader>
        </div>
    )
})
