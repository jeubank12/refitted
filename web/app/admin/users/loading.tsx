import styles from 'styles/Loading.module.css'

const Loading = () => (
  <div className={styles.stage}>
    <div className={styles['dot-flashing']} />
  </div>
)

export default Loading
